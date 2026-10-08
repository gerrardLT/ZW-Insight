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
                .filter(log -> "CONFIRMED".equals(log.getStatus())
                        && "UNSETTLED".equals(log.getSettlementStatus()))
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
        if (tenant == null) throw new BusinessException("缺少租户上下文");
        Map<Long, List<BizMachineWorkLog>> logsByMachine = new TreeMap<>();
        Map<Long, BizMachineContract> machineContractMap = new HashMap<>();
        for (BizMachineWorkLog candidate : workLogs.stream().sorted(Comparator.comparing(BizMachineWorkLog::getId)).toList()) {
            BizMachineWorkLog locked = workLogMapper.lockById(candidate.getId(), tenant);
            if (locked == null || !Objects.equals(tenant, locked.getTenantId())
                    || !Objects.equals(projectId, locked.getProjectId()) || locked.getWorkDate() == null
                    || locked.getWorkDate().isBefore(periodStart) || locked.getWorkDate().isAfter(periodEnd)
                    || !"CONFIRMED".equals(locked.getStatus())
                    || !"UNSETTLED".equals(locked.getSettlementStatus())
                    || workLogMapper.countOccupied(locked.getId(), tenant) != 0) {
                throw new BusinessException("日志未确认、已结算或被其他结算单占用");
            }
            BizMachineContract contract = contractMapper.selectById(locked.getContractId());
            if (contract == null || !Objects.equals(tenant, contract.getTenantId())
                    || !Objects.equals(projectId, contract.getProjectId()) || !"EFFECTIVE".equals(contract.getStatus())
                    || contract.getUnitPrice() == null || contract.getUnitPrice().signum() <= 0
                    || contract.getStartDate() == null || contract.getEndDate() == null
                    || locked.getWorkDate().isBefore(contract.getStartDate()) || locked.getWorkDate().isAfter(contract.getEndDate())) {
                throw new BusinessException("日志必须绑定本项目生效合同及有效单价");
            }
            machineContractMap.put(contract.getId(), contract);
            logsByMachine.computeIfAbsent(contract.getId(), k -> new ArrayList<>()).add(locked);
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
            Long contractId = entry.getKey();
            List<BizMachineWorkLog> machineLogs = entry.getValue();

            BizMachineWorkSettlementDetail detail = new BizMachineWorkSettlementDetail();
            detail.setSettlementId(settlement.getId());
            detail.setLedgerId(machineLogs.get(0).getMachineId());
            detail.setContractId(contractId);
            detail.setWorkLogIds(machineLogs.stream().map(BizMachineWorkLog::getId).collect(Collectors.toList()));
            detail.setTenantId(tenantId);
            detail.setCreatedAt(LocalDateTime.now());

            // 汇总台班数和工作量
            BigDecimal totalShiftCount = machineLogs.stream()
                    .map(l -> l.getShiftCount() != null ? l.getShiftCount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalWorkVolume = machineLogs.stream()
                    .map(l -> l.getWorkQuantity() != null ? l.getWorkQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            detail.setShiftCount(totalShiftCount);
            detail.setWorkVolume(totalWorkVolume);

            BizMachineContract contract = machineContractMap.get(contractId);
            String rentalType = contract.getRentalType();
            BigDecimal quantity;
            if ("SHIFT".equals(rentalType) || "台班".equals(rentalType)) {
                detail.setPricingType("SHIFT");
                quantity = totalShiftCount;
            } else if ("VOLUME".equals(rentalType) || "工作量".equals(rentalType)) {
                detail.setPricingType("VOLUME");
                quantity = totalWorkVolume;
            } else if (List.of("MONTHLY", "月租", "包月").contains(rentalType == null ? "" : rentalType)) {
                detail.setPricingType("MONTHLY");
                // 按合同周期自然日折月：每月覆盖天数/月天数；合同内多机械只计一次。
                LocalDate start = periodStart.isAfter(contract.getStartDate()) ? periodStart : contract.getStartDate();
                LocalDate end = periodEnd.isBefore(contract.getEndDate()) ? periodEnd : contract.getEndDate();
                quantity = BigDecimal.ZERO;
                for (LocalDate day = start; !day.isAfter(end); ) {
                    LocalDate monthEnd = YearMonth.from(day).atEndOfMonth();
                    LocalDate last = monthEnd.isBefore(end) ? monthEnd : end;
                    quantity = quantity.add(BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(day, last) + 1)
                            .divide(BigDecimal.valueOf(day.lengthOfMonth()), 10, RoundingMode.HALF_UP));
                    day = last.plusDays(1);
                }
                quantity = quantity.setScale(4, RoundingMode.HALF_UP);
            } else {
                throw new BusinessException("不支持的机械计价类型");
            }
            if (quantity.signum() <= 0) throw new BusinessException("计价数量必须大于零");
            detail.setBillingQuantity(quantity);
            detail.setUnitPrice(contract.getUnitPrice());
            BigDecimal subtotal = quantity.multiply(contract.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
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
        if (tenantId == null) throw new BusinessException("缺少租户上下文");
        BizMachineWorkSettlement settlement = settlementMapper.lockById(settlementId, tenantId);
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
        if (tenant == null || !Objects.equals(tenant, settlement.getTenantId())) throw new BusinessException("结算单不存在");
        if (Integer.valueOf(2).equals(settlement.getStatus())) return;
        if (!Integer.valueOf(1).equals(settlement.getStatus())) throw new BusinessException("仅审批中结算可生效");
        var cas = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizMachineWorkSettlement>();
        cas.eq(BizMachineWorkSettlement::getId, settlementId).eq(BizMachineWorkSettlement::getTenantId, tenant)
                .eq(BizMachineWorkSettlement::getStatus, 1).set(BizMachineWorkSettlement::getStatus, 2);
        if (settlementMapper.update(null, cas) != 1) {
            BizMachineWorkSettlement current = settlementMapper.selectById(settlementId);
            if (current != null && Objects.equals(tenant, current.getTenantId()) && Integer.valueOf(2).equals(current.getStatus())) return;
            throw new BusinessException("结算状态已变更");
        }
        var detailWrapper = new LambdaQueryWrapper<BizMachineWorkSettlementDetail>();
        detailWrapper.eq(BizMachineWorkSettlementDetail::getSettlementId, settlementId)
                .eq(BizMachineWorkSettlementDetail::getTenantId, tenant);
        List<BizMachineWorkSettlementDetail> details = detailMapper.selectList(detailWrapper);
        if (details.isEmpty()) throw new BusinessException("结算明细缺失");
        Map<Long, BigDecimal> increments = new TreeMap<>();
        Set<Long> ids = new TreeSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BizMachineWorkSettlementDetail detail : details) {
            if (detail.getContractId() == null || detail.getSubtotal() == null || detail.getSubtotal().signum() <= 0
                    || detail.getWorkLogIds() == null || detail.getWorkLogIds().isEmpty()) throw new BusinessException("结算明细无有效合同或日志");
            increments.merge(detail.getContractId(), detail.getSubtotal(), BigDecimal::add);
            total = total.add(detail.getSubtotal());
            for (Long id : detail.getWorkLogIds()) {
                if (!ids.add(id)) throw new BusinessException("结算日志重复引用");
                BizMachineWorkLog workLog = workLogMapper.lockById(id, tenant);
                if (workLog == null || !Objects.equals(workLog.getProjectId(), settlement.getProjectId())
                        || !Objects.equals(workLog.getContractId(), detail.getContractId())
                        || !"CONFIRMED".equals(workLog.getStatus()) || !"UNSETTLED".equals(workLog.getSettlementStatus())) {
                    throw new BusinessException("日志绑定或状态已变更");
                }
                workLog.setSettlementStatus("SETTLED");
                if (workLogMapper.updateById(workLog) != 1) throw new BusinessException("日志结算回写失败");
            }
        }
        if (total.compareTo(settlement.getTotalAmount()) != 0) throw new BusinessException("结算主表与明细金额不一致");
        for (var increment : increments.entrySet()) {
            if (contractMapper.addSettlement(increment.getKey(), increment.getValue(), tenant, settlement.getProjectId()) != 1) {
                throw new BusinessException("合同失效或累计结算超合同额，审批已回滚");
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

        BigDecimal contractSettled = contracts.stream().map(c -> c.getCumulativeSettlement() == null
                ? BigDecimal.ZERO : c.getCumulativeSettlement()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalSettled.compareTo(contractSettled) != 0 || totalPaid.compareTo(totalSettled) > 0) {
            throw new BusinessException("机械结算与合同累计或付款口径不一致，请核对历史数据");
        }
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
        if (settlement == null || SecurityContextHolder.getTenantId() == null
                || !Objects.equals(settlement.getTenantId(), SecurityContextHolder.getTenantId())) {
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
