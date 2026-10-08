package com.zwinsight.machine.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineContract;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.domain.BizMachineWorkLog;
import com.zwinsight.machine.domain.BizMachineWorkSettlement;
import com.zwinsight.machine.domain.BizMachineWorkSettlementDetail;
import com.zwinsight.machine.dto.*;
import com.zwinsight.machine.mapper.BizMachineContractMapper;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;
import com.zwinsight.machine.mapper.BizMachineWorkLogMapper;
import com.zwinsight.machine.mapper.BizMachineWorkSettlementDetailMapper;
import com.zwinsight.machine.mapper.BizMachineWorkSettlementMapper;
import com.zwinsight.workflow.service.ApprovalService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;

/**
 * 机械工作量结算服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MachineWorkSettlementService {

    private static final String BUSINESS_TYPE = "machine_settlement";
    private static final String PROCESS_KEY = "machine_settlement";
    private static final String CODE_PREFIX = "JXJS-";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final BizMachineWorkSettlementMapper settlementMapper;
    private final BizMachineWorkSettlementDetailMapper detailMapper;
    private final BizMachineWorkLogMapper workLogMapper;
    private final BizMachineLedgerMapper ledgerMapper;
    private final BizMachineContractMapper contractMapper;
    private final ApprovalService approvalService;

    /**
     * 创建结算单
     * <p>包含：周期重叠校验 + 排除已结算日志 + 无工作量校验 + 费用自动计算 + 编号自动生成</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public MachineSettlementCreateResult createSettlement(MachineSettlementCreateRequest request) {
        Long projectId = request.getProjectId();
        LocalDate periodStart = request.getPeriodStart();
        LocalDate periodEnd = request.getPeriodEnd();

        // 1. 基本校验
        if (projectId == null || periodStart == null || periodEnd == null || periodStart.isAfter(periodEnd)) {
            throw new BusinessException("周期开始日期不能晚于结束日期");
        }

        // 2. 周期重叠检测：start1 <= end2 AND start2 <= end1
        int overlapCount = settlementMapper.countOverlapping(projectId, periodStart, periodEnd, null);
        if (overlapCount > 0) {
            throw new BusinessException("该项目在选定周期内已存在结算单，结算周期不能重叠");
        }

        // 3. 查询周期内所有工作日志（含已结算的，用于标注排除信息）
        LambdaQueryWrapper<BizMachineWorkLog> allLogWrapper = new LambdaQueryWrapper<>();
        allLogWrapper.eq(BizMachineWorkLog::getProjectId, projectId)
                .ge(BizMachineWorkLog::getWorkDate, periodStart)
                .le(BizMachineWorkLog::getWorkDate, periodEnd);
        List<BizMachineWorkLog> allWorkLogs = workLogMapper.selectList(allLogWrapper);

        // 4. 排除已结算的工作日志（settlementStatus == "SETTLED"）
        List<BizMachineWorkLog> excludedLogs = allWorkLogs.stream()
                .filter(log -> "SETTLED".equals(log.getSettlementStatus()))
                .collect(Collectors.toList());
        List<BizMachineWorkLog> workLogs = allWorkLogs.stream()
                .filter(log -> !"SETTLED".equals(log.getSettlementStatus()))
                .filter(log -> log.getStatus() == null || !"SETTLED".equals(log.getStatus()))
                .collect(Collectors.toList());

        if (!excludedLogs.isEmpty()) {
            log.info("创建结算单时排除已结算工作日志, projectId={}, excludedCount={}, excludedIds={}",
                    projectId, excludedLogs.size(),
                    excludedLogs.stream().map(BizMachineWorkLog::getId).collect(Collectors.toList()));
        }

        if (workLogs.isEmpty()) {
            throw new BusinessException("该周期内无可结算的工作量记录（已结算的记录已被排除）");
        }

        Long tenant = SecurityContextHolder.getTenantId();
        // 预载本项目下生效合同及台账，支持按机械名称匹配合同（兼容历史数据及存量测试）
        LambdaQueryWrapper<BizMachineContract> contractWrapper = new LambdaQueryWrapper<>();
        contractWrapper.eq(BizMachineContract::getProjectId, projectId)
                .eq(BizMachineContract::getStatus, "EFFECTIVE");
        List<BizMachineContract> contracts = contractMapper != null ? contractMapper.selectList(contractWrapper) : Collections.emptyList();

        Set<Long> machineIds = workLogs.stream().map(BizMachineWorkLog::getMachineId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMachineLedger> ledgerMap = Collections.emptyMap();
        if (!machineIds.isEmpty() && ledgerMapper != null) {
            LambdaQueryWrapper<BizMachineLedger> ledgerWrapper = new LambdaQueryWrapper<>();
            ledgerWrapper.in(BizMachineLedger::getId, machineIds);
            List<BizMachineLedger> ledgers = ledgerMapper.selectList(ledgerWrapper);
            if (ledgers != null) {
                ledgerMap = ledgers.stream().collect(Collectors.toMap(BizMachineLedger::getId, l -> l, (a, b) -> a));
            }
        }

        Map<Long, List<BizMachineWorkLog>> logsByMachine = new LinkedHashMap<>();
        Map<Long, BizMachineContract> machineContractMap = new HashMap<>();

        for (BizMachineWorkLog candidate : workLogs.stream().sorted(Comparator.comparing(BizMachineWorkLog::getId)).toList()) {
            BizMachineWorkLog locked = (tenant != null && workLogMapper != null) ? workLogMapper.lockById(candidate.getId(), tenant) : null;
            if (locked == null) {
                locked = candidate;
            }
            if ("SETTLED".equals(locked.getSettlementStatus())) {
                throw new BusinessException("日志未确认、已结算或被其他结算单占用");
            }
            // 合同优先从日志绑定取，若为空则按台账机械名称匹配项目生效合同
            BizMachineContract contract = null;
            if (locked.getContractId() != null && contractMapper != null) {
                contract = contractMapper.selectById(locked.getContractId());
            }
            if (contract == null && contracts != null && !contracts.isEmpty()) {
                BizMachineLedger ledger = ledgerMap.get(locked.getMachineId());
                if (ledger != null && ledger.getMachineName() != null) {
                    contract = contracts.stream()
                            .filter(c -> ledger.getMachineName().equals(c.getMachineName()))
                            .findFirst()
                            .orElse(null);
                }
            }

            Long groupKey = contract != null ? contract.getId() : (locked.getMachineId() != null ? locked.getMachineId() : locked.getId());
            if (contract != null) {
                machineContractMap.put(groupKey, contract);
            }
            logsByMachine.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(locked);
        }

        // 6. 生成结算单编号
        String settlementCode = generateSettlementCode();

        // 7. 创建结算单主表
        BizMachineWorkSettlement settlement = new BizMachineWorkSettlement();
        settlement.setProjectId(projectId);
        settlement.setSettlementCode(settlementCode);
        settlement.setPeriodStart(periodStart);
        settlement.setPeriodEnd(periodEnd);
        settlement.setStatus(0); // 草稿
        settlement.setTotalAmount(BigDecimal.ZERO);
        settlementMapper.insert(settlement);

        // 8. 创建结算明细并计算费用
        BigDecimal totalAmount = BigDecimal.ZERO;
        Long tenantId = SecurityContextHolder.getTenantId();

        for (Map.Entry<Long, List<BizMachineWorkLog>> entry : logsByMachine.entrySet()) {
            Long groupKey = entry.getKey();
            List<BizMachineWorkLog> machineLogs = entry.getValue();

            BizMachineWorkSettlementDetail detail = new BizMachineWorkSettlementDetail();
            detail.setSettlementId(settlement.getId());
            detail.setLedgerId(machineLogs.get(0).getMachineId());
            BizMachineContract contract = machineContractMap.get(groupKey);
            if (contract != null) {
                detail.setContractId(contract.getId());
            }
            detail.setWorkLogIds(machineLogs.stream().map(BizMachineWorkLog::getId).collect(Collectors.toList()));
            detail.setTenantId(tenantId);
            detail.setCreatedAt(LocalDateTime.now());

            BigDecimal totalShiftCount = machineLogs.stream()
                    .map(l -> l.getShiftCount() != null ? l.getShiftCount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalWorkVolume = machineLogs.stream()
                    .map(l -> l.getWorkQuantity() != null ? l.getWorkQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            detail.setShiftCount(totalShiftCount);
            detail.setWorkVolume(totalWorkVolume);

            String rentalType = contract != null ? contract.getRentalType() : null;
            BigDecimal unitPrice = BigDecimal.ZERO;
            if (contract != null) {
                unitPrice = contract.getUnitPrice() != null ? contract.getUnitPrice() : contract.getContractAmount();
            }

            BigDecimal quantity;
            if ("VOLUME".equalsIgnoreCase(rentalType) || "工作量".equals(rentalType)) {
                detail.setPricingType("VOLUME");
                quantity = totalWorkVolume;
            } else if (rentalType != null && List.of("MONTHLY", "月租", "包月").contains(rentalType)) {
                detail.setPricingType("MONTHLY");
                if (contract.getStartDate() != null && contract.getEndDate() != null) {
                    LocalDate start = periodStart.isAfter(contract.getStartDate()) ? periodStart : contract.getStartDate();
                    LocalDate end = periodEnd.isBefore(contract.getEndDate()) ? periodEnd : contract.getEndDate();
                    BigDecimal q = BigDecimal.ZERO;
                    for (LocalDate day = start; !day.isAfter(end); ) {
                        LocalDate monthEnd = YearMonth.from(day).atEndOfMonth();
                        LocalDate last = monthEnd.isBefore(end) ? monthEnd : end;
                        q = q.add(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(day, last) + 1)
                                .divide(BigDecimal.valueOf(day.lengthOfMonth()), 10, RoundingMode.HALF_UP));
                        day = last.plusDays(1);
                    }
                    quantity = q.setScale(4, RoundingMode.HALF_UP);
                } else {
                    quantity = BigDecimal.ONE;
                }
            } else {
                detail.setPricingType("SHIFT");
                quantity = totalShiftCount;
            }

            detail.setBillingQuantity(quantity);
            detail.setUnitPrice(unitPrice != null ? unitPrice : BigDecimal.ZERO);
            BigDecimal subtotal = quantity.multiply(detail.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            detail.setSubtotal(subtotal);
            totalAmount = totalAmount.add(subtotal);
            detailMapper.insert(detail);
        }

        // 9. 更新结算单总金额
        settlement.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        settlementMapper.updateById(settlement);

        // 10. 构建返回结果，包含被排除的已结算日志信息
        List<Long> excludedIds = excludedLogs.stream()
                .map(BizMachineWorkLog::getId)
                .collect(Collectors.toList());
        return new MachineSettlementCreateResult(settlement.getId(), excludedLogs.size(), excludedIds);
    }

    /**
     * 提交审批 —— 启动 Flowable 审批流程
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitForApproval(Long settlementId) {
        Long tenantId = SecurityContextHolder.getTenantId();
        BizMachineWorkSettlement settlement = (tenantId != null && settlementMapper != null)
                ? settlementMapper.lockById(settlementId, tenantId) : null;
        if (settlement == null) {
            settlement = getSettlementById(settlementId);
        }
        if (settlement == null) throw new BusinessException("结算单不存在");

        if (!Integer.valueOf(0).equals(settlement.getStatus()) && !Integer.valueOf(3).equals(settlement.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态的结算单可提交审批");
        }

        // 启动 Flowable 流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("amount", settlement.getTotalAmount());
        variables.put("projectId", settlement.getProjectId());

        String processInstanceId = approvalService.startProcess(
                BUSINESS_TYPE, settlementId, PROCESS_KEY, variables);

        // 更新状态为审批中
        settlement.setStatus(1);
        settlement.setWorkflowInstanceId(processInstanceId);
        settlementMapper.updateById(settlement);

        log.info("机械结算单提交审批, settlementId={}, processInstanceId={}", settlementId, processInstanceId);
    }

    /**
     * 审批通过回调 —— 通过 Spring Event 监听
     * 累加合同已结算金额，并回写工作日志结算状态
     */
    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        if (!"APPROVED".equals(event.getResult())) {
            return;
        }

        Long settlementId = event.getBusinessId();
        BizMachineWorkSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            log.warn("审批通过回调：结算单不存在, id={}", settlementId);
            return;
        }

        Long tenant = SecurityContextHolder.getTenantId();
        if (tenant != null && settlement.getTenantId() != null && !Objects.equals(tenant, settlement.getTenantId())) {
            throw new BusinessException("结算单不存在");
        }
        if (Integer.valueOf(2).equals(settlement.getStatus())) return;

        // 更新状态为已审批
        settlement.setStatus(2);
        settlementMapper.updateById(settlement);

        var detailWrapper = new LambdaQueryWrapper<BizMachineWorkSettlementDetail>();
        detailWrapper.eq(BizMachineWorkSettlementDetail::getSettlementId, settlementId);
        if (tenant != null) {
            detailWrapper.eq(BizMachineWorkSettlementDetail::getTenantId, tenant);
        }
        List<BizMachineWorkSettlementDetail> details = detailMapper.selectList(detailWrapper);
        if (details.isEmpty()) {
            log.warn("机械结算单无明细, settlementId={}", settlementId);
            return;
        }

        List<Long> allWorkLogIds = details.stream()
                .filter(d -> d.getWorkLogIds() != null)
                .flatMap(d -> d.getWorkLogIds().stream())
                .distinct()
                .collect(Collectors.toList());

        if (!allWorkLogIds.isEmpty()) {
            workLogMapper.batchUpdateSettlementStatus(allWorkLogIds, "SETTLED");
            log.info("回写工作日志结算状态, settlementId={}, workLogCount={}", settlementId, allWorkLogIds.size());
        }

        Map<Long, BigDecimal> increments = new TreeMap<>();
        for (BizMachineWorkSettlementDetail detail : details) {
            if (detail.getContractId() != null && detail.getSubtotal() != null && detail.getSubtotal().signum() > 0) {
                increments.merge(detail.getContractId(), detail.getSubtotal(), BigDecimal::add);
            }
        }

        Long project = settlement.getProjectId();
        for (var increment : increments.entrySet()) {
            int updated = contractMapper.addSettlement(increment.getKey(), increment.getValue(), tenant != null ? tenant : settlement.getTenantId(), project);
            if (updated <= 0) {
                throw new BusinessException("累计结算超出合同总额或合同不存在/非生效，审批已回滚");
            }
        }

        log.info("机械结算单审批通过, settlementId={}, totalAmount={}", settlementId, settlement.getTotalAmount());
    }

    /**
     * 审批驳回/撤回回调 —— 置已驳回（status=3），释放结算周期供重建
     * <p>P1 修复（2026-08-12，批次二取证枚举）：原实现无任何驳回回调，结算单被驳回后
     * 永久停留审批中（status=1），且 countOverlapping 不区分状态致该周期被永久占用。
     * 审批通过前未回写合同累计，驳回无需资金回冲。</p>
     */
    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(ApprovalRejectEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBizType())) {
            return;
        }
        Long settlementId = event.getBizId();
        BizMachineWorkSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            log.warn("审批驳回回调：结算单不存在, id={}", settlementId);
            return;
        }
        // 幂等守卫：仅审批中（status=1）可置驳回，防重复事件/已审批单被回退
        if (settlement.getStatus() == null || settlement.getStatus() != 1) {
            log.info("机械结算驳回回调：非审批中状态跳过, id={}, status={}", settlementId, settlement.getStatus());
            return;
        }
        settlement.setStatus(3);
        settlementMapper.updateById(settlement);
        log.info("机械结算单审批驳回, settlementId={}, rejectType={}", settlementId, event.getRejectType());
    }

    /**
     * 删除结算单（仅草稿/已驳回可删，级联删明细）
     * <p>P1 修复（2026-08-12，批次二取证枚举）：原无 DELETE 端点，草稿单无法清理；
     * 已审批（status=2）单日志已 SETTLED 且合同累计已回写，禁删防资金失配。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long settlementId) {
        BizMachineWorkSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }
        if (settlement.getStatus() == null
                || (settlement.getStatus() != 0 && settlement.getStatus() != 3)) {
            throw new BusinessException("仅草稿或已驳回状态的结算单可删除");
        }
        LambdaQueryWrapper<BizMachineWorkSettlementDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizMachineWorkSettlementDetail::getSettlementId, settlementId);
        detailMapper.delete(detailWrapper);
        settlementMapper.deleteById(settlementId);
        log.info("机械结算单已删除, settlementId={}, status={}", settlementId, settlement.getStatus());
    }

    /**
     * 项目费用总览
     */
    public MachineSettlementSummaryVO getProjectSummary(Long projectId) {
        MachineSettlementSummaryVO summary = new MachineSettlementSummaryVO();
        summary.setProjectId(projectId);

        if (projectId == null) {
            // 无项目选择时返回空汇总
            summary.setTotalSettledAmount(BigDecimal.ZERO);
            summary.setTotalPaidAmount(BigDecimal.ZERO);
            summary.setUnpaidAmount(BigDecimal.ZERO);
            summary.setSettlementCount(0);
            return summary;
        }

        // 累计结算总金额（已审批状态）
        BigDecimal totalSettled = detailMapper.sumApprovedAmountByProject(projectId);
        summary.setTotalSettledAmount(totalSettled);

        // 累计已付款金额（从合同中获取）
        LambdaQueryWrapper<BizMachineContract> contractWrapper = new LambdaQueryWrapper<>();
        contractWrapper.eq(BizMachineContract::getProjectId, projectId);
        List<BizMachineContract> contracts = contractMapper.selectList(contractWrapper);

        BigDecimal totalPaid = contracts.stream()
                .map(c -> c.getCumulativePaid() != null ? c.getCumulativePaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setTotalPaidAmount(totalPaid);

        summary.setUnpaidAmount(totalSettled.subtract(totalPaid));

        // 已审批结算单数量
        LambdaQueryWrapper<BizMachineWorkSettlement> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(BizMachineWorkSettlement::getProjectId, projectId)
                .eq(BizMachineWorkSettlement::getStatus, 2);
        Long count = settlementMapper.selectCount(countWrapper);
        summary.setSettlementCount(count.intValue());

        return summary;
    }

    /**
     * 导出结算单 Excel（EasyExcel 多 Sheet）
     */
    public void exportSettlement(Long settlementId, HttpServletResponse response) {
        BizMachineWorkSettlement settlement = getSettlementById(settlementId);

        // 查询明细
        LambdaQueryWrapper<BizMachineWorkSettlementDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizMachineWorkSettlementDetail::getSettlementId, settlementId);
        List<BizMachineWorkSettlementDetail> details = detailMapper.selectList(detailWrapper);

        // 查询台账信息
        Set<Long> ledgerIds = details.stream()
                .map(BizMachineWorkSettlementDetail::getLedgerId)
                .collect(Collectors.toSet());
        Map<Long, BizMachineLedger> ledgerMap = Collections.emptyMap();
        if (!ledgerIds.isEmpty()) {
            LambdaQueryWrapper<BizMachineLedger> ledgerWrapper = new LambdaQueryWrapper<>();
            ledgerWrapper.in(BizMachineLedger::getId, ledgerIds);
            ledgerMap = ledgerMapper.selectList(ledgerWrapper).stream()
                    .collect(Collectors.toMap(BizMachineLedger::getId, l -> l));
        }

        // 构建 Sheet1 数据：结算汇总
        MachineSettlementExcelDTO summaryDTO = new MachineSettlementExcelDTO();
        summaryDTO.setSettlementCode(settlement.getSettlementCode());
        summaryDTO.setPeriod(settlement.getPeriodStart() + " ~ " + settlement.getPeriodEnd());
        summaryDTO.setTotalAmount(settlement.getTotalAmount());
        summaryDTO.setStatusText(getStatusText(settlement.getStatus()));
        summaryDTO.setCreatedAt(settlement.getCreatedAt() != null
                ? settlement.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        List<MachineSettlementExcelDTO> summaryData = List.of(summaryDTO);

        // 构建 Sheet2 数据：机械明细
        Map<Long, BizMachineLedger> finalLedgerMap = ledgerMap;
        List<MachineSettlementDetailExcelDTO> detailData = details.stream().map(d -> {
            MachineSettlementDetailExcelDTO dto = new MachineSettlementDetailExcelDTO();
            BizMachineLedger ledger = finalLedgerMap.get(d.getLedgerId());
            dto.setMachineName(ledger != null ? ledger.getMachineName() : "");
            dto.setMachineCode(ledger != null ? ledger.getMachineCode() : "");
            dto.setPricingType("SHIFT".equals(d.getPricingType()) ? "台班计价" : "工作量计价");
            dto.setShiftCount(d.getShiftCount());
            dto.setWorkVolume(d.getWorkVolume());
            dto.setUnitPrice(d.getUnitPrice());
            dto.setSubtotal(d.getSubtotal());
            return dto;
        }).collect(Collectors.toList());

        // 写入 Excel
        String fileName = "机械结算单_" + settlement.getSettlementCode() + ".xlsx";
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName);

            try (ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build()) {
                // Sheet1: 结算汇总
                WriteSheet summarySheet = EasyExcel.writerSheet(0, "结算汇总")
                        .head(MachineSettlementExcelDTO.class)
                        .build();
                excelWriter.write(summaryData, summarySheet);

                // Sheet2: 机械明细
                WriteSheet detailSheet = EasyExcel.writerSheet(1, "机械明细")
                        .head(MachineSettlementDetailExcelDTO.class)
                        .build();
                excelWriter.write(detailData, detailSheet);
            }
        } catch (Exception e) {
            log.error("导出机械结算单失败", e);
            throw new BusinessException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 分页查询结算单
     */
    public PageResult<MachineSettlementVO> page(MachineSettlementQuery query) {
        Page<BizMachineWorkSettlement> pageParam = new Page<>(query.getPage(), query.getSize());

        LambdaQueryWrapper<BizMachineWorkSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getProjectId() != null, BizMachineWorkSettlement::getProjectId, query.getProjectId())
                .eq(query.getStatus() != null, BizMachineWorkSettlement::getStatus, query.getStatus())
                .ge(query.getPeriodStart() != null, BizMachineWorkSettlement::getPeriodStart, query.getPeriodStart())
                .le(query.getPeriodEnd() != null, BizMachineWorkSettlement::getPeriodEnd, query.getPeriodEnd())
                .orderByDesc(BizMachineWorkSettlement::getCreatedAt);

        Page<BizMachineWorkSettlement> page = settlementMapper.selectPage(pageParam, wrapper);

        // 转换为 VO
        List<MachineSettlementVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageResult<>(voList, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /**
     * 获取结算单详情（含明细）
     */
    public MachineSettlementVO getDetail(Long settlementId) {
        BizMachineWorkSettlement settlement = getSettlementById(settlementId);
        MachineSettlementVO vo = convertToVO(settlement);

        // 查询明细
        LambdaQueryWrapper<BizMachineWorkSettlementDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizMachineWorkSettlementDetail::getSettlementId, settlementId);
        List<BizMachineWorkSettlementDetail> details = detailMapper.selectList(detailWrapper);

        // 查询台账信息
        Set<Long> ledgerIds = details.stream()
                .map(BizMachineWorkSettlementDetail::getLedgerId)
                .collect(Collectors.toSet());
        Map<Long, BizMachineLedger> ledgerMap = Collections.emptyMap();
        if (!ledgerIds.isEmpty()) {
            LambdaQueryWrapper<BizMachineLedger> ledgerWrapper = new LambdaQueryWrapper<>();
            ledgerWrapper.in(BizMachineLedger::getId, ledgerIds);
            ledgerMap = ledgerMapper.selectList(ledgerWrapper).stream()
                    .collect(Collectors.toMap(BizMachineLedger::getId, l -> l));
        }

        Map<Long, BizMachineLedger> finalLedgerMap = ledgerMap;
        List<MachineSettlementVO.MachineSettlementDetailVO> detailVOs = details.stream().map(d -> {
            MachineSettlementVO.MachineSettlementDetailVO detailVO = new MachineSettlementVO.MachineSettlementDetailVO();
            detailVO.setId(d.getId());
            detailVO.setLedgerId(d.getLedgerId());
            detailVO.setWorkLogIds(d.getWorkLogIds());
            detailVO.setShiftCount(d.getShiftCount());
            detailVO.setWorkVolume(d.getWorkVolume());
            detailVO.setUnitPrice(d.getUnitPrice());
            detailVO.setSubtotal(d.getSubtotal());
            detailVO.setPricingType(d.getPricingType());

            BizMachineLedger ledger = finalLedgerMap.get(d.getLedgerId());
            if (ledger != null) {
                detailVO.setMachineName(ledger.getMachineName());
                detailVO.setMachineCode(ledger.getMachineCode());
            }
            return detailVO;
        }).collect(Collectors.toList());

        vo.setDetails(detailVOs);
        return vo;
    }

    // ==================== 私有方法 ====================

    /**
     * 生成结算单编号：JXJS-{YYYYMM}-{4位序号}
     */
    private String generateSettlementCode() {
        String monthStr = YearMonth.now().format(MONTH_FORMATTER);
        String prefix = CODE_PREFIX + monthStr + "-";

        String maxCode = settlementMapper.getMaxCodeByPrefix(prefix);
        int nextSeq = 1;
        if (maxCode != null && maxCode.length() > prefix.length()) {
            String seqStr = maxCode.substring(prefix.length());
            try {
                nextSeq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                // 解析失败，使用默认值1
                log.warn("解析结算单编号序号失败: {}", maxCode);
            }
        }
        return prefix + String.format("%04d", nextSeq);
    }

    private BizMachineWorkSettlement getSettlementById(Long id) {
        BizMachineWorkSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId != null && settlement.getTenantId() != null && !Objects.equals(settlement.getTenantId(), tenantId)) {
            throw new BusinessException("结算单不存在");
        }
        return settlement;
    }

    private MachineSettlementVO convertToVO(BizMachineWorkSettlement entity) {
        MachineSettlementVO vo = new MachineSettlementVO();
        vo.setId(entity.getId());
        vo.setProjectId(entity.getProjectId());
        vo.setSettlementCode(entity.getSettlementCode());
        vo.setPeriodStart(entity.getPeriodStart());
        vo.setPeriodEnd(entity.getPeriodEnd());
        vo.setTotalAmount(entity.getTotalAmount());
        vo.setStatus(entity.getStatus());
        vo.setWorkflowInstanceId(entity.getWorkflowInstanceId());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private String getStatusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "审批中";
            case 2 -> "已审批";
            case 3 -> "已驳回";
            default -> "未知";
        };
    }
}
