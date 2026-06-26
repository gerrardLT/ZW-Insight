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
        if (periodStart.isAfter(periodEnd)) {
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
                .collect(Collectors.toList());

        if (!excludedLogs.isEmpty()) {
            log.info("创建结算单时排除已结算工作日志, projectId={}, excludedCount={}, excludedIds={}",
                    projectId, excludedLogs.size(),
                    excludedLogs.stream().map(BizMachineWorkLog::getId).collect(Collectors.toList()));
        }

        if (workLogs.isEmpty()) {
            throw new BusinessException("该周期内无可结算的工作量记录（已结算的记录已被排除）");
        }

        // 4. 按机械（machineId）分组计算费用
        Map<Long, List<BizMachineWorkLog>> logsByMachine = workLogs.stream()
                .collect(Collectors.groupingBy(BizMachineWorkLog::getMachineId));

        // 5. 查询关联的机械合同，获取单价和计价方式
        // 从机械合同中获取单价信息（通过项目ID关联）
        LambdaQueryWrapper<BizMachineContract> contractWrapper = new LambdaQueryWrapper<>();
        contractWrapper.eq(BizMachineContract::getProjectId, projectId)
                .eq(BizMachineContract::getStatus, "EFFECTIVE");
        List<BizMachineContract> contracts = contractMapper.selectList(contractWrapper);

        // 建立机械到合同的映射（通过供应商或名称匹配合同）
        Map<Long, BizMachineContract> machineContractMap = new HashMap<>();
        if (!contracts.isEmpty()) {
            // 查询台账获取机械名称
            Set<Long> machineIds = logsByMachine.keySet();
            LambdaQueryWrapper<BizMachineLedger> ledgerWrapper = new LambdaQueryWrapper<>();
            ledgerWrapper.in(BizMachineLedger::getId, machineIds);
            List<BizMachineLedger> ledgers = ledgerMapper.selectList(ledgerWrapper);
            Map<Long, BizMachineLedger> ledgerMap = ledgers.stream()
                    .collect(Collectors.toMap(BizMachineLedger::getId, l -> l));

            for (Long machineId : machineIds) {
                BizMachineLedger ledger = ledgerMap.get(machineId);
                if (ledger != null) {
                    // 通过机械名称匹配合同
                    contracts.stream()
                            .filter(c -> ledger.getMachineName().equals(c.getMachineName()))
                            .findFirst()
                            .ifPresent(c -> machineContractMap.put(machineId, c));
                }
            }
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
            Long machineId = entry.getKey();
            List<BizMachineWorkLog> machineLogs = entry.getValue();

            BizMachineWorkSettlementDetail detail = new BizMachineWorkSettlementDetail();
            detail.setSettlementId(settlement.getId());
            detail.setLedgerId(machineId);
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

            // 获取合同确定计价方式和单价
            BizMachineContract contract = machineContractMap.get(machineId);
            BigDecimal subtotal;

            if (contract != null) {
                String rentalType = contract.getRentalType();
                BigDecimal unitPrice = contract.getContractAmount(); // 合同单价信息

                if ("台班".equals(rentalType) || "SHIFT".equalsIgnoreCase(rentalType)) {
                    // 台班计价：subtotal = shiftCount × unitPrice
                    detail.setPricingType("SHIFT");
                    detail.setUnitPrice(unitPrice);
                    subtotal = totalShiftCount.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
                } else {
                    // 工作量计价：subtotal = workVolume × unitPrice
                    detail.setPricingType("VOLUME");
                    detail.setUnitPrice(unitPrice);
                    subtotal = totalWorkVolume.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
                }
            } else {
                // 无合同关联时，默认台班计价，单价为0
                detail.setPricingType("SHIFT");
                detail.setUnitPrice(BigDecimal.ZERO);
                subtotal = BigDecimal.ZERO;
            }

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
        BizMachineWorkSettlement settlement = getSettlementById(settlementId);

        if (settlement.getStatus() != 0 && settlement.getStatus() != 3) {
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

        // 更新状态为已审批
        settlement.setStatus(2);
        settlementMapper.updateById(settlement);

        // 回写工作日志的结算状态为"已结算"
        LambdaQueryWrapper<BizMachineWorkSettlementDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizMachineWorkSettlementDetail::getSettlementId, settlementId);
        List<BizMachineWorkSettlementDetail> details = detailMapper.selectList(detailWrapper);

        List<Long> allWorkLogIds = details.stream()
                .filter(d -> d.getWorkLogIds() != null)
                .flatMap(d -> d.getWorkLogIds().stream())
                .distinct()
                .collect(Collectors.toList());

        if (!allWorkLogIds.isEmpty()) {
            workLogMapper.batchUpdateSettlementStatus(allWorkLogIds, "SETTLED");
            log.info("回写工作日志结算状态, settlementId={}, workLogCount={}", settlementId, allWorkLogIds.size());
        }

        // 累加合同已结算金额 —— 查找该项目关联的生效合同
        LambdaQueryWrapper<BizMachineContract> contractWrapper = new LambdaQueryWrapper<>();
        contractWrapper.eq(BizMachineContract::getProjectId, settlement.getProjectId())
                .eq(BizMachineContract::getStatus, "EFFECTIVE");
        List<BizMachineContract> contracts = contractMapper.selectList(contractWrapper);

        if (!contracts.isEmpty()) {
            // 将总金额按合同数量均摊（如仅1个合同则全额累加）
            // 实际业务中通常1个项目对应多个机械合同，按明细对应累加更精确
            // 这里简化处理：累加到第一个生效合同
            BizMachineContract contract = contracts.get(0);
            BigDecimal cumulative = contract.getCumulativeSettlement() != null
                    ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
            contract.setCumulativeSettlement(cumulative.add(settlement.getTotalAmount()));
            contractMapper.updateById(contract);
        }

        log.info("机械结算单审批通过, settlementId={}, totalAmount={}", settlementId, settlement.getTotalAmount());
    }

    /**
     * 项目费用总览
     */
    public MachineSettlementSummaryVO getProjectSummary(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }

        MachineSettlementSummaryVO summary = new MachineSettlementSummaryVO();
        summary.setProjectId(projectId);

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

        // 未付款金额 = 已结算 - 已付款
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
