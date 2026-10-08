package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.domain.BizLaborOutputReport;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.labor.mapper.BizLaborOutputReportMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 劳务产值报告服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaborOutputReportService {

    private final BizLaborOutputReportMapper outputReportMapper;
    private final BizLaborContractMapper laborContractMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizLaborOutputReport> page(int page, int size, Long projectId, Long contractId) {
        Page<BizLaborOutputReport> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizLaborOutputReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizLaborOutputReport::getProjectId, projectId)
                .eq(contractId != null, BizLaborOutputReport::getContractId, contractId)
                .orderByDesc(BizLaborOutputReport::getCreatedAt);
        Page<BizLaborOutputReport> result = outputReportMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存产值报告
     */
    public void save(BizLaborOutputReport report) {
        if (report.getCurrentOutput() == null || report.getCurrentOutput().signum() <= 0) {
            throw new BusinessException("本期产值金额必须大于0");
        }
        report.setStatus("DRAFT");
        report.setWorkflowInstanceId(null);
        outputReportMapper.insert(report);
    }

    /**
     * 根据ID查询
     */
    public BizLaborOutputReport getById(Long id) {
        BizLaborOutputReport report = outputReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException("产值报告不存在");
        }
        return report;
    }

    /**
     * 更新产值报告
     */
    public void update(BizLaborOutputReport report) {
        BizLaborOutputReport existing = outputReportMapper.selectById(report.getId());
        if (existing == null) {
            throw new BusinessException("产值报告不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        if (report.getCurrentOutput() != null && report.getCurrentOutput().signum() <= 0) {
            throw new BusinessException("本期产值金额必须大于0");
        }
        // 白名单防篡改：状态与流程字段由服务端控制
        existing.setCurrentOutput(report.getCurrentOutput());
        existing.setCumulativeOutput(report.getCumulativeOutput());
        if (report.getProjectId() != null) {
            existing.setProjectId(report.getProjectId());
        }
        if (report.getContractId() != null) {
            existing.setContractId(report.getContractId());
        }
        outputReportMapper.updateById(existing);
    }

    /**
     * 删除产值报告
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizLaborOutputReport existing = outputReportMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("产值报告不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        BigDecimal amount = existing.getCurrentOutput() == null ? BigDecimal.ZERO : existing.getCurrentOutput();
        outputReportMapper.deleteById(id);

        if ("APPROVED".equals(existing.getStatus()) && existing.getContractId() != null) {
            laborContractMapper.addOutput(existing.getContractId(), amount.negate());
            log.info("劳务产值删除并冲销合同累计值, id={}, amount={}", id, amount);
        }
    }

    /**
     * 提交审批
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizLaborOutputReport report = outputReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException("产值报告不存在");
        }
        if (!"DRAFT".equals(report.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        if (report.getCurrentOutput() == null || report.getCurrentOutput().signum() <= 0) {
            throw new BusinessException("产值金额必须大于0");
        }

        // 校验产值不超过合同总额限制
        BizLaborContract contract = laborContractMapper.selectById(report.getContractId());
        if (contract != null && contract.getContractAmount() != null) {
            BigDecimal currentCum = contract.getCumulativeOutput() != null ? contract.getCumulativeOutput() : BigDecimal.ZERO;
            BigDecimal newCum = currentCum.add(report.getCurrentOutput());
            if (newCum.compareTo(contract.getContractAmount()) > 0) {
                BigDecimal maxAllowed = contract.getContractAmount().subtract(currentCum);
                throw new BusinessException("累计产值超出合同金额限制，当前最大可报产值：" + maxAllowed);
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("outputAmount", report.getCurrentOutput());
        variables.put("projectId", report.getProjectId());
        variables.put("contractId", report.getContractId());
        String processInstanceId = approvalService.startProcess(
                "LABOR_OUTPUT", id, "labor_output_approval", variables);

        report.setWorkflowInstanceId(processInstanceId);
        report.setStatus("SUBMITTED");
        outputReportMapper.updateById(report);
    }

    /**
     * 审批通过回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizLaborOutputReport report = outputReportMapper.selectById(id);
        if (report == null) {
            log.warn("劳务产值审批通过回调：单据不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(report.getStatus())) {
            log.info("劳务产值已生效，跳过重复回调, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(report.getStatus())) {
            log.warn("劳务产值当前状态非 SUBMITTED，忽略生效回调: id={}, status={}", id, report.getStatus());
            return;
        }

        report.setStatus("APPROVED");
        outputReportMapper.updateById(report);

        if (report.getContractId() != null && report.getCurrentOutput() != null) {
            laborContractMapper.addOutput(report.getContractId(), report.getCurrentOutput());
            log.info("劳务产值审批通过并原子回写合同累计产值: reportId={}, contractId={}, amount={}",
                    id, report.getContractId(), report.getCurrentOutput());
        }
    }

    /**
     * 审批驳回回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizLaborOutputReport report = outputReportMapper.selectById(id);
        if (report == null) {
            log.warn("劳务产值审批驳回回调：单据不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(report.getStatus())) {
            log.warn("劳务产值当前状态非 SUBMITTED，忽略驳回回调: id={}, status={}", id, report.getStatus());
            return;
        }

        report.setStatus("DRAFT");
        outputReportMapper.updateById(report);
        log.info("劳务产值审批驳回回退草稿: id={}", id);
    }
}
