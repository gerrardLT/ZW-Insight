package com.zwinsight.finance.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizProjectSettlement;
import com.zwinsight.finance.domain.BizSettlementContractDetail;
import com.zwinsight.finance.domain.dto.ExpenseContractInfo;
import com.zwinsight.finance.domain.dto.SettlementDetailExcelDTO;
import com.zwinsight.finance.domain.dto.SettlementSummaryExcelDTO;
import com.zwinsight.finance.mapper.BizProjectSettlementMapper;
import com.zwinsight.finance.mapper.BizSettlementContractDetailMapper;
import com.zwinsight.finance.mapper.SettlementDataMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import com.zwinsight.workflow.service.ApprovalService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目最终结算服务
 * <p>
 * 负责项目竣工后的最终结算数据汇总、利润计算、合同明细生成及未结清合同查询。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectSettlementService {

    private final BizProjectSettlementMapper settlementMapper;
    private final BizSettlementContractDetailMapper detailMapper;
    private final BizProjectMapper projectMapper;
    private final BizConstructionContractMapper constructionContractMapper;
    private final SettlementDataMapper settlementDataMapper;
    private final ApprovalService approvalService;

    /**
     * 创建结算单（自动汇总数据）
     *
     * @param projectId 项目ID
     * @return 结算单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createSettlement(Long projectId) {
        // 1. 校验项目状态必须为"已竣工"
        BizProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (!"COMPLETED".equals(project.getStatus())) {
            throw new BusinessException("项目未竣工，无法进行最终结算");
        }

        // 2. 校验不存在草稿/审批中结算单（同一项目仅允许一份进行中的结算单）
        LambdaQueryWrapper<BizProjectSettlement> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(BizProjectSettlement::getProjectId, projectId)
                .in(BizProjectSettlement::getStatus, "DRAFT", "SUBMITTED");
        Long existingCount = settlementMapper.selectCount(existingWrapper);
        if (existingCount > 0) {
            throw new BusinessException("该项目已存在进行中的结算单");
        }

        // 3. 汇总收入数据
        // 施工合同总额 & 累计产值 — 取该项目下施工合同合计
        LambdaQueryWrapper<BizConstructionContract> contractWrapper = new LambdaQueryWrapper<>();
        contractWrapper.eq(BizConstructionContract::getProjectId, projectId)
                .eq(BizConstructionContract::getContractType, "REGISTER");
        List<BizConstructionContract> contracts = constructionContractMapper.selectList(contractWrapper);

        BigDecimal constructionContractAmount = BigDecimal.ZERO;
        BigDecimal cumulativeOutput = BigDecimal.ZERO;
        for (BizConstructionContract contract : contracts) {
            if (contract.getContractAmount() != null) {
                constructionContractAmount = constructionContractAmount.add(contract.getContractAmount());
            }
            if (contract.getCumulativeOutput() != null) {
                cumulativeOutput = cumulativeOutput.add(contract.getCumulativeOutput());
            }
        }

        // 累计收款
        BigDecimal cumulativeReceived = settlementDataMapper.sumReceivedByProject(projectId);
        // 累计开票
        BigDecimal cumulativeInvoiced = settlementDataMapper.sumInvoicedByProject(projectId);
        // 总收入 = 累计收款
        BigDecimal totalIncome = cumulativeReceived;

        // 4. 汇总支出数据
        BigDecimal subcontractSettled = settlementDataMapper.sumSubcontractSettlement(projectId);
        BigDecimal laborSettled = settlementDataMapper.sumLaborSettlement(projectId);
        BigDecimal materialSettled = settlementDataMapper.sumMaterialSettlement(projectId);
        BigDecimal machineSettled = settlementDataMapper.sumMachineSettlement(projectId);
        BigDecimal cumulativePaid = settlementDataMapper.sumPaymentByProject(projectId);

        // 总支出 = 分包结算 + 劳务结算 + 材料结算 + 机械结算 + 累计付款
        BigDecimal totalExpenditure = subcontractSettled
                .add(laborSettled)
                .add(materialSettled)
                .add(machineSettled)
                .add(cumulativePaid);

        // 5. 计算利润（精确到分）
        BigDecimal profit = totalIncome.subtract(totalExpenditure)
                .setScale(2, RoundingMode.HALF_UP);

        // 计算利润率（totalIncome > 0 时，精确到小数点后2位）
        BigDecimal profitRate;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            profitRate = profit.divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            profitRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // 6. 创建结算单
        BizProjectSettlement settlement = new BizProjectSettlement();
        settlement.setProjectId(projectId);
        settlement.setSettlementCode(generateSettlementCode(projectId));
        settlement.setConstructionContractAmount(constructionContractAmount.setScale(2, RoundingMode.HALF_UP));
        settlement.setCumulativeOutput(cumulativeOutput.setScale(2, RoundingMode.HALF_UP));
        settlement.setCumulativeReceived(cumulativeReceived.setScale(2, RoundingMode.HALF_UP));
        settlement.setCumulativeInvoiced(cumulativeInvoiced.setScale(2, RoundingMode.HALF_UP));
        settlement.setTotalIncome(totalIncome.setScale(2, RoundingMode.HALF_UP));
        settlement.setSubcontractSettled(subcontractSettled.setScale(2, RoundingMode.HALF_UP));
        settlement.setLaborSettled(laborSettled.setScale(2, RoundingMode.HALF_UP));
        settlement.setMaterialSettled(materialSettled.setScale(2, RoundingMode.HALF_UP));
        settlement.setMachineSettled(machineSettled.setScale(2, RoundingMode.HALF_UP));
        settlement.setOtherExpense(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        settlement.setCumulativePaid(cumulativePaid.setScale(2, RoundingMode.HALF_UP));
        settlement.setTotalExpenditure(totalExpenditure.setScale(2, RoundingMode.HALF_UP));
        settlement.setProfit(profit);
        settlement.setProfitRate(profitRate);
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);

        // 7. 生成关联合同明细并标注未结清合同
        generateContractDetails(settlement.getId(), projectId);

        return settlement.getId();
    }

    /**
     * 分页查询结算单列表
     *
     * @param page      页码
     * @param size      每页数量
     * @param projectId 项目ID（可选）
     * @param status    结算单状态（可选）
     * @return 分页结果
     */
    public PageResult<BizProjectSettlement> page(int page, int size, Long projectId, String status) {
        Page<BizProjectSettlement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizProjectSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizProjectSettlement::getProjectId, projectId)
                .eq(status != null && !status.isEmpty(), BizProjectSettlement::getStatus, status)
                .orderByDesc(BizProjectSettlement::getCreatedAt);
        Page<BizProjectSettlement> result = settlementMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizProjectSettlement::getProjectId, BizProjectSettlement::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询结算单详情
     *
     * @param id 结算单ID
     * @return 结算单实体
     */
    public BizProjectSettlement getById(Long id) {
        BizProjectSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }
        return settlement;
    }

    /**
     * 编辑结算单（仅 DRAFT/REJECTED 状态可编辑）
     * <p>
     * 驳回后允许财务人员修改并重新提交。
     * 可选择重新汇总数据或仅修改其他支出字段。
     * </p>
     *
     * @param id         结算单ID
     * @param updateDTO  编辑请求参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSettlement(Long id, com.zwinsight.finance.domain.dto.ProjectSettlementUpdateDTO updateDTO) {
        BizProjectSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }
        if (!"DRAFT".equals(settlement.getStatus()) && !"REJECTED".equals(settlement.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可编辑结算单");
        }

        if (Boolean.TRUE.equals(updateDTO.getResummarize())) {
            // 重新汇总收支数据
            Long projectId = settlement.getProjectId();

            // 汇总收入
            LambdaQueryWrapper<BizConstructionContract> contractWrapper = new LambdaQueryWrapper<>();
            contractWrapper.eq(BizConstructionContract::getProjectId, projectId)
                    .eq(BizConstructionContract::getContractType, "REGISTER");
            List<BizConstructionContract> contracts = constructionContractMapper.selectList(contractWrapper);

            BigDecimal constructionContractAmount = BigDecimal.ZERO;
            BigDecimal cumulativeOutput = BigDecimal.ZERO;
            for (BizConstructionContract contract : contracts) {
                if (contract.getContractAmount() != null) {
                    constructionContractAmount = constructionContractAmount.add(contract.getContractAmount());
                }
                if (contract.getCumulativeOutput() != null) {
                    cumulativeOutput = cumulativeOutput.add(contract.getCumulativeOutput());
                }
            }

            BigDecimal cumulativeReceived = settlementDataMapper.sumReceivedByProject(projectId);
            BigDecimal cumulativeInvoiced = settlementDataMapper.sumInvoicedByProject(projectId);
            BigDecimal totalIncome = cumulativeReceived;

            // 汇总支出
            BigDecimal subcontractSettled = settlementDataMapper.sumSubcontractSettlement(projectId);
            BigDecimal laborSettled = settlementDataMapper.sumLaborSettlement(projectId);
            BigDecimal materialSettled = settlementDataMapper.sumMaterialSettlement(projectId);
            BigDecimal machineSettled = settlementDataMapper.sumMachineSettlement(projectId);
            BigDecimal cumulativePaid = settlementDataMapper.sumPaymentByProject(projectId);

            BigDecimal otherExpense = updateDTO.getOtherExpense() != null ?
                    updateDTO.getOtherExpense() : settlement.getOtherExpense();

            BigDecimal totalExpenditure = subcontractSettled
                    .add(laborSettled)
                    .add(materialSettled)
                    .add(machineSettled)
                    .add(cumulativePaid)
                    .add(otherExpense);

            BigDecimal profit = totalIncome.subtract(totalExpenditure)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal profitRate;
            if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
                profitRate = profit.divide(totalIncome, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            } else {
                profitRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }

            settlement.setConstructionContractAmount(constructionContractAmount.setScale(2, RoundingMode.HALF_UP));
            settlement.setCumulativeOutput(cumulativeOutput.setScale(2, RoundingMode.HALF_UP));
            settlement.setCumulativeReceived(cumulativeReceived.setScale(2, RoundingMode.HALF_UP));
            settlement.setCumulativeInvoiced(cumulativeInvoiced.setScale(2, RoundingMode.HALF_UP));
            settlement.setTotalIncome(totalIncome.setScale(2, RoundingMode.HALF_UP));
            settlement.setSubcontractSettled(subcontractSettled.setScale(2, RoundingMode.HALF_UP));
            settlement.setLaborSettled(laborSettled.setScale(2, RoundingMode.HALF_UP));
            settlement.setMaterialSettled(materialSettled.setScale(2, RoundingMode.HALF_UP));
            settlement.setMachineSettled(machineSettled.setScale(2, RoundingMode.HALF_UP));
            settlement.setOtherExpense(otherExpense.setScale(2, RoundingMode.HALF_UP));
            settlement.setCumulativePaid(cumulativePaid.setScale(2, RoundingMode.HALF_UP));
            settlement.setTotalExpenditure(totalExpenditure.setScale(2, RoundingMode.HALF_UP));
            settlement.setProfit(profit);
            settlement.setProfitRate(profitRate);

            // 重新生成合同明细
            detailMapper.delete(new LambdaQueryWrapper<BizSettlementContractDetail>()
                    .eq(BizSettlementContractDetail::getSettlementId, id));
            generateContractDetails(id, projectId);
        } else {
            // 仅更新其他支出字段
            if (updateDTO.getOtherExpense() != null) {
                settlement.setOtherExpense(updateDTO.getOtherExpense().setScale(2, RoundingMode.HALF_UP));

                // 重新计算总支出和利润
                BigDecimal totalExpenditure = settlement.getSubcontractSettled()
                        .add(settlement.getLaborSettled())
                        .add(settlement.getMaterialSettled())
                        .add(settlement.getMachineSettled())
                        .add(settlement.getCumulativePaid())
                        .add(settlement.getOtherExpense());
                settlement.setTotalExpenditure(totalExpenditure.setScale(2, RoundingMode.HALF_UP));

                BigDecimal profit = settlement.getTotalIncome().subtract(totalExpenditure)
                        .setScale(2, RoundingMode.HALF_UP);
                settlement.setProfit(profit);

                if (settlement.getTotalIncome().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal profitRate = profit.divide(settlement.getTotalIncome(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                    settlement.setProfitRate(profitRate);
                }
            }
        }

        // 驳回后编辑，状态回到 DRAFT
        if ("REJECTED".equals(settlement.getStatus())) {
            settlement.setStatus("DRAFT");
        }

        settlementMapper.updateById(settlement);
        log.info("结算单编辑成功, settlementId={}, resummarize={}", id, updateDTO.getResummarize());
    }

    /**
     * 查询未结清合同列表
     * <p>
     * 合同结算状态非"已完结"或已结算金额小于合同金额的分包/劳务/材料/机械合同
     * </p>
     *
     * @param projectId 项目ID
     * @return 未结清合同明细列表
     */
    public List<BizSettlementContractDetail> getUnsettledContracts(Long projectId) {
        // 查询该项目最新结算单
        LambdaQueryWrapper<BizProjectSettlement> settlementWrapper = new LambdaQueryWrapper<>();
        settlementWrapper.eq(BizProjectSettlement::getProjectId, projectId)
                .orderByDesc(BizProjectSettlement::getCreatedAt)
                .last("LIMIT 1");
        BizProjectSettlement settlement = settlementMapper.selectOne(settlementWrapper);
        if (settlement == null) {
            return new ArrayList<>();
        }

        // 查询该结算单中未结清的合同明细
        LambdaQueryWrapper<BizSettlementContractDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizSettlementContractDetail::getSettlementId, settlement.getId())
                .eq(BizSettlementContractDetail::getSettlementStatus, "UNSETTLED");
        return detailMapper.selectList(detailWrapper);
    }

    /**
     * 提交结算单审批
     * <p>
     * 校验结算单存在且状态为 DRAFT，调用审批服务启动流程，
     * 更新状态为 SUBMITTED 并记录 workflowInstanceId。
     * </p>
     *
     * @param settlementId 结算单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long settlementId) {
        BizProjectSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }
        if (!"DRAFT".equals(settlement.getStatus()) && !"REJECTED".equals(settlement.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可提交审批");
        }

        // 构建流程变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("businessType", "PROJECT_SETTLEMENT");
        variables.put("projectId", settlement.getProjectId());
        variables.put("amount", settlement.getProfit());

        // 启动审批流程
        String processInstanceId = approvalService.startProcess(
                "PROJECT_SETTLEMENT", settlementId, "project_settlement_approval", variables);

        // 更新状态和流程实例ID
        settlement.setStatus("SUBMITTED");
        settlement.setWorkflowInstanceId(processInstanceId);
        settlementMapper.updateById(settlement);

        log.info("结算单提交审批成功, settlementId={}, processInstanceId={}", settlementId, processInstanceId);
    }

    /**
     * 审批通过回调
     * <p>
     * 更新结算单状态为 APPROVED，同时将关联项目状态更新为 CLOSED。
     * </p>
     *
     * @param settlementId 结算单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long settlementId) {
        BizProjectSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }

        // 更新结算单状态为 APPROVED
        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        // 更新项目状态为 CLOSED
        projectMapper.updateStatus(settlement.getProjectId(), "CLOSED");

        log.info("结算单审批通过, settlementId={}, 项目状态更新为CLOSED, projectId={}",
                settlementId, settlement.getProjectId());
    }

    /**
     * 审批驳回回调
     * <p>
     * 更新结算单状态为 REJECTED，项目状态不变。
     * </p>
     *
     * @param settlementId 结算单ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long settlementId) {
        BizProjectSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }

        settlement.setStatus("REJECTED");
        settlementMapper.updateById(settlement);

        log.info("结算单审批驳回, settlementId={}", settlementId);
    }

    /**
     * 导出结算报告 Excel
     * <p>
     * 多 Sheet 写入：
     * - Sheet 1: 收支汇总表（项目基本信息 + 收入汇总 + 支出汇总 + 利润）
     * - Sheet 2: 合同结算明细（合同类型、编号、名称、金额、已结算、已付、未结金额、状态）
     * </p>
     *
     * @param settlementId 结算单ID
     * @param response     HTTP 响应对象
     */
    public void exportExcel(Long settlementId, HttpServletResponse response) {
        // 1. 查询结算单详情
        BizProjectSettlement settlement = settlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new BusinessException("结算单不存在");
        }

        // 2. 查询项目名称
        BizProject project = projectMapper.selectById(settlement.getProjectId());
        String projectName = project != null ? project.getProjectName() : "";

        // 3. 构建收支汇总数据
        SettlementSummaryExcelDTO summaryDTO = new SettlementSummaryExcelDTO();
        summaryDTO.setSettlementCode(settlement.getSettlementCode());
        summaryDTO.setProjectName(projectName);
        summaryDTO.setConstructionContractAmount(settlement.getConstructionContractAmount());
        summaryDTO.setCumulativeOutput(settlement.getCumulativeOutput());
        summaryDTO.setCumulativeReceived(settlement.getCumulativeReceived());
        summaryDTO.setCumulativeInvoiced(settlement.getCumulativeInvoiced());
        summaryDTO.setTotalIncome(settlement.getTotalIncome());
        summaryDTO.setSubcontractSettled(settlement.getSubcontractSettled());
        summaryDTO.setLaborSettled(settlement.getLaborSettled());
        summaryDTO.setMaterialSettled(settlement.getMaterialSettled());
        summaryDTO.setMachineSettled(settlement.getMachineSettled());
        summaryDTO.setCumulativePaid(settlement.getCumulativePaid());
        summaryDTO.setTotalExpenditure(settlement.getTotalExpenditure());
        summaryDTO.setProfit(settlement.getProfit());
        summaryDTO.setProfitRate(settlement.getProfitRate());

        // 4. 查询合同明细数据
        LambdaQueryWrapper<BizSettlementContractDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizSettlementContractDetail::getSettlementId, settlementId);
        List<BizSettlementContractDetail> contractDetails = detailMapper.selectList(detailWrapper);

        List<SettlementDetailExcelDTO> detailDTOs = new ArrayList<>();
        for (BizSettlementContractDetail detail : contractDetails) {
            SettlementDetailExcelDTO dto = new SettlementDetailExcelDTO();
            dto.setContractType(translateContractType(detail.getContractType()));
            dto.setContractCode(detail.getContractCode());
            dto.setContractName(detail.getContractName());
            dto.setContractAmount(detail.getContractAmount());
            dto.setSettledAmount(detail.getSettledAmount());
            dto.setPaidAmount(detail.getPaidAmount());
            dto.setUnsettledAmount(detail.getUnsettledAmount());
            dto.setSettlementStatus(translateSettlementStatus(detail.getSettlementStatus()));
            detailDTOs.add(dto);
        }

        // 5. 使用 EasyExcel 多 Sheet 写入
        try (ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build()) {
            // Sheet 1: 收支汇总表
            WriteSheet summarySheet = EasyExcel.writerSheet(0, "收支汇总表")
                    .head(SettlementSummaryExcelDTO.class)
                    .build();
            excelWriter.write(Collections.singletonList(summaryDTO), summarySheet);

            // Sheet 2: 合同结算明细
            WriteSheet detailSheet = EasyExcel.writerSheet(1, "合同结算明细")
                    .head(SettlementDetailExcelDTO.class)
                    .build();
            excelWriter.write(detailDTOs, detailSheet);
        } catch (IOException e) {
            log.error("导出结算报告Excel失败, settlementId={}", settlementId, e);
            throw new BusinessException("导出Excel失败: " + e.getMessage());
        }
    }

    /**
     * 合同类型编码转中文
     */
    private String translateContractType(String contractType) {
        if (contractType == null) {
            return "";
        }
        switch (contractType) {
            case "SUBCONTRACT":
                return "分包合同";
            case "LABOR":
                return "劳务合同";
            case "MATERIAL":
                return "材料合同";
            case "MACHINE":
                return "机械合同";
            default:
                return contractType;
        }
    }

    /**
     * 结算状态编码转中文
     */
    private String translateSettlementStatus(String status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case "SETTLED":
                return "已结清";
            case "UNSETTLED":
                return "未结清";
            default:
                return status;
        }
    }

    /**
     * 生成结算单编号
     * 格式: JS-{projectId}-{yyyyMMddHHmmss}
     */
    private String generateSettlementCode(Long projectId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "JS-" + projectId + "-" + timestamp;
    }

    /**
     * 生成关联合同明细
     * 查询项目下所有支出类合同（分包/劳务/材料/机械），并标注未结清状态
     */
    private void generateContractDetails(Long settlementId, Long projectId) {
        List<BizSettlementContractDetail> details = new ArrayList<>();

        // 分包合同明细
        details.addAll(buildSubcontractDetails(settlementId, projectId));
        // 劳务合同明细
        details.addAll(buildLaborDetails(settlementId, projectId));
        // 材料（采购）合同明细
        details.addAll(buildMaterialDetails(settlementId, projectId));
        // 机械合同明细
        details.addAll(buildMachineDetails(settlementId, projectId));

        // 批量插入
        for (BizSettlementContractDetail detail : details) {
            detailMapper.insert(detail);
        }
    }

    /**
     * 构建分包合同明细 — 直接查表避免模块耦合
     */
    private List<BizSettlementContractDetail> buildSubcontractDetails(Long settlementId, Long projectId) {
        // 使用 settlementDataMapper 模式直接查询原始数据，构建明细
        // 此处通过 MyBatis-Plus selectList 兼容方式处理
        return buildExpenseContractDetails(settlementId, projectId, "SUBCONTRACT",
                "biz_subcontract");
    }

    private List<BizSettlementContractDetail> buildLaborDetails(Long settlementId, Long projectId) {
        return buildExpenseContractDetails(settlementId, projectId, "LABOR",
                "biz_labor_contract");
    }

    private List<BizSettlementContractDetail> buildMaterialDetails(Long settlementId, Long projectId) {
        return buildExpenseContractDetails(settlementId, projectId, "MATERIAL",
                "biz_purchase_contract");
    }

    private List<BizSettlementContractDetail> buildMachineDetails(Long settlementId, Long projectId) {
        return buildExpenseContractDetails(settlementId, projectId, "MACHINE",
                "biz_machine_contract");
    }

    /**
     * 通用支出合同明细构建
     * 通过 SettlementContractDetailMapper 使用自定义 SQL 查询各类支出合同
     */
    private List<BizSettlementContractDetail> buildExpenseContractDetails(
            Long settlementId, Long projectId, String contractType, String tableName) {
        // 通过 settlementDataMapper 无法直接 selectList，故使用 detailMapper 的自定义方式
        // 实际使用内置的 selectByMap 或手动构建
        List<BizSettlementContractDetail> details = new ArrayList<>();

        // 查询各类合同的基本信息用于构建明细
        List<ExpenseContractInfo> contractInfos = queryExpenseContracts(projectId, tableName);

        for (ExpenseContractInfo info : contractInfos) {
            BizSettlementContractDetail detail = new BizSettlementContractDetail();
            detail.setSettlementId(settlementId);
            detail.setContractType(contractType);
            detail.setContractId(info.getId());
            detail.setContractCode(info.getContractCode());
            detail.setContractName(info.getContractName());
            detail.setContractAmount(info.getContractAmount() != null ?
                    info.getContractAmount() : BigDecimal.ZERO);
            detail.setSettledAmount(info.getCumulativeSettlement() != null ?
                    info.getCumulativeSettlement() : BigDecimal.ZERO);
            detail.setPaidAmount(info.getCumulativePaid() != null ?
                    info.getCumulativePaid() : BigDecimal.ZERO);

            // 计算未结金额 = 合同金额 - 已结算金额
            BigDecimal unsettledAmount = detail.getContractAmount()
                    .subtract(detail.getSettledAmount());
            detail.setUnsettledAmount(unsettledAmount.setScale(2, RoundingMode.HALF_UP));

            // 标注合同结算状态：已结算金额 >= 合同金额 视为已结清
            if (detail.getSettledAmount().compareTo(detail.getContractAmount()) >= 0) {
                detail.setSettlementStatus("SETTLED");
            } else {
                detail.setSettlementStatus("UNSETTLED");
            }

            details.add(detail);
        }

        return details;
    }

    /**
     * 查询支出合同基本信息
     */
    private List<ExpenseContractInfo> queryExpenseContracts(Long projectId, String tableName) {
        return settlementMapper.selectExpenseContracts(projectId, tableName);
    }
}
