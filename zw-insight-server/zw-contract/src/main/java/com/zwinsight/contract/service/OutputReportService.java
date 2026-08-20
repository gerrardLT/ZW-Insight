package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.event.UrgeNotifyEvent;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.domain.BizOutputReportDetail;
import com.zwinsight.contract.mapper.BizBoqItemMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportDetailMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 产值报告服务
 * <p>
 * 审批后生效模式：submit 仅校验并启动流程（状态 SUBMITTED），
 * 审批通过后由 {@link com.zwinsight.contract.listener.OutputReportApprovalListener}
 * 回调 {@link #onApproved(Long)} 回写合同/项目累计产值及 BOQ 已完成工程量；
 * 驳回回调 {@link #onRejected(Long)}，数据未生效无需回滚。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutputReportService {

    private final BizOutputReportMapper outputReportMapper;
    private final BizOutputReportDetailMapper reportDetailMapper;
    private final BizConstructionContractMapper contractMapper;
    private final BizBoqItemMapper boqItemMapper;
    private final BizProjectMapper projectMapper;
    private final ApprovalService approvalService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 分页查询
     */
    public PageResult<BizOutputReport> page(int page, int size, Long projectId, Long contractId) {
        Page<BizOutputReport> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizOutputReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizOutputReport::getProjectId, projectId)
                .eq(contractId != null, BizOutputReport::getContractId, contractId)
                .orderByDesc(BizOutputReport::getCreatedAt);
        Page<BizOutputReport> result = outputReportMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存产值报告（草稿，含可选的清单明细行）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizOutputReport report) {
        report.setStatus("DRAFT");
        outputReportMapper.insert(report);
        saveDetails(report.getId(), report.getDetails());
    }

    /**
     * 提交审批（校验累计产值上限后启动流程，状态置 SUBMITTED，不回写累计数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizOutputReport report = outputReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException("产值报告不存在");
        }
        if (!"DRAFT".equals(report.getStatus()) && !"REJECTED".equals(report.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可提交");
        }

        BizConstructionContract contract = contractMapper.selectById(report.getContractId());
        if (contract == null) {
            throw new BusinessException("关联合同不存在");
        }
        validateOutputLimit(contract, report.getCurrentOutput());

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("currentOutput", report.getCurrentOutput());
        variables.put("projectId", report.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "OUTPUT_REPORT", id, "output_report_approval", variables);

        report.setWorkflowInstanceId(processInstanceId);
        report.setStatus("SUBMITTED");
        outputReportMapper.updateById(report);
    }

    /**
     * 审批通过回调：回写合同/项目累计产值（SQL 原子累加）+ BOQ 已完成工程量
     * <p>幂等：状态已为 APPROVED 时直接返回（兼容存量在途单据与重复事件）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizOutputReport report = outputReportMapper.selectById(id);
        if (report == null) {
            log.warn("产值报告审批通过回调：报告不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(report.getStatus())) {
            log.info("产值报告已生效，跳过重复回调, id={}", id);
            return;
        }

        BizConstructionContract contract = contractMapper.selectById(report.getContractId());
        if (contract == null) {
            log.error("产值报告审批通过回调：关联合同不存在, reportId={}, contractId={}", id, report.getContractId());
            return;
        }

        // 审批期间上限可能变化，生效前重新校验；不通过则置 REJECTED 并通知发起人
        BigDecimal newCumulativeOutput;
        try {
            newCumulativeOutput = validateOutputLimit(contract, report.getCurrentOutput());
        } catch (BusinessException e) {
            report.setStatus("REJECTED");
            outputReportMapper.updateById(report);
            notifyInitiator(report, "产值上报生效失败", e.getMessage());
            log.warn("产值报告生效校验失败已驳回, id={}, reason={}", id, e.getMessage());
            return;
        }

        // 回写合同/项目累计产值（原子累加）
        contractMapper.addCumulativeOutput(report.getContractId(), report.getCurrentOutput());
        projectMapper.addCumulativeOutput(report.getProjectId(), report.getCurrentOutput());

        // 回写 BOQ 清单条目已完成工程量
        List<BizOutputReportDetail> details = listDetails(id);
        for (BizOutputReportDetail detail : details) {
            if (detail.getBoqItemId() != null && detail.getQuantity() != null) {
                boqItemMapper.addCompletedQuantity(detail.getBoqItemId(), detail.getQuantity());
            }
        }

        report.setStatus("APPROVED");
        report.setCumulativeOutput(newCumulativeOutput);
        outputReportMapper.updateById(report);

        log.info("产值报告审批通过并生效, id={}, currentOutput={}, boqDetails={}",
                id, report.getCurrentOutput(), details.size());
    }

    /**
     * 审批驳回/撤回回调：状态置 REJECTED（数据未生效，无需回滚金额）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizOutputReport report = outputReportMapper.selectById(id);
        if (report == null) {
            log.warn("产值报告驳回回调：报告不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(report.getStatus())) {
            return;
        }
        report.setStatus("REJECTED");
        outputReportMapper.updateById(report);
        log.info("产值报告审批驳回, id={}", id);
    }

    /**
     * 删除产值报告（2026-08-21 台账缺口修复：补齐 DELETE 通道）
     * <p>
     * 状态守卫：仅 DRAFT/REJECTED 可删（数据未生效，无累计产值回滚问题）；
     * E2E 测试数据（主表 String 字段带 E2E_TEST_ 前缀）旁路放行供测试清理，
     * 与 BudgetService/SubcontractService 等删除守卫同款双条件模式。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizOutputReport existing = outputReportMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("产值报告不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !"REJECTED".equals(existing.getStatus())
                && !E2eTestGuard.containsE2eTestMarker(existing)) {
            throw new BusinessException("仅草稿或已驳回状态可删除");
        }
        LambdaQueryWrapper<BizOutputReportDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizOutputReportDetail::getReportId, id);
        reportDetailMapper.delete(detailWrapper);
        outputReportMapper.deleteById(id);
    }

    /**
     * 查询产值报告的清单明细行
     */
    public List<BizOutputReportDetail> listDetails(Long reportId) {
        LambdaQueryWrapper<BizOutputReportDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizOutputReportDetail::getReportId, reportId);
        return reportDetailMapper.selectList(wrapper);
    }

    /**
     * 校验累计产值不超过合同金额（含变更）
     *
     * @return 校验通过后的新累计产值
     */
    private BigDecimal validateOutputLimit(BizConstructionContract contract, BigDecimal currentOutput) {
        BigDecimal maxOutput = contract.getContractAmount()
                .add(contract.getCumulativeChangeAmount() != null ? contract.getCumulativeChangeAmount() : BigDecimal.ZERO);
        BigDecimal newCumulativeOutput = (contract.getCumulativeOutput() != null ? contract.getCumulativeOutput() : BigDecimal.ZERO)
                .add(currentOutput);
        if (newCumulativeOutput.compareTo(maxOutput) > 0) {
            throw new BusinessException("累计产值不能超过合同金额（含变更），当前上限：" + maxOutput);
        }
        return newCumulativeOutput;
    }

    /**
     * 保存明细行（金额 = 数量 × 清单综合单价，保留2位小数）
     */
    private void saveDetails(Long reportId, List<BizOutputReportDetail> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (BizOutputReportDetail detail : details) {
            detail.setId(null);
            detail.setReportId(reportId);
            reportDetailMapper.insert(detail);
        }
    }

    /**
     * 站内信通知发起人（生效失败等异常场景，不静默）
     */
    private void notifyInitiator(BizOutputReport report, String title, String content) {
        if (report.getCreatedBy() == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(new UrgeNotifyEvent(
                    this, report.getCreatedBy(), title, content, report.getWorkflowInstanceId(), null));
        } catch (Exception e) {
            log.error("发送产值上报通知失败, reportId={}", report.getId(), e);
        }
    }
}
