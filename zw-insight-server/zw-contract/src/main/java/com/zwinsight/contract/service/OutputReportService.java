package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 产值报告服务
 */
@Service
@RequiredArgsConstructor
public class OutputReportService {

    private final BizOutputReportMapper outputReportMapper;
    private final BizConstructionContractMapper contractMapper;
    private final BizProjectMapper projectMapper;
    private final ApprovalService approvalService;

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
     * 保存产值报告（草稿）
     */
    public void save(BizOutputReport report) {
        report.setStatus("DRAFT");
        outputReportMapper.insert(report);
    }

    /**
     * 提交审批（审批通过→回写合同和项目累计产值，校验不超合同金额含变更）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizOutputReport report = outputReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException("产值报告不存在");
        }
        if (!"DRAFT".equals(report.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 获取合同信息
        BizConstructionContract contract = contractMapper.selectById(report.getContractId());
        if (contract == null) {
            throw new BusinessException("关联合同不存在");
        }

        // 计算最大可报产值 = 合同金额 + 累计变更金额
        BigDecimal maxOutput = contract.getContractAmount()
                .add(contract.getCumulativeChangeAmount() != null ? contract.getCumulativeChangeAmount() : BigDecimal.ZERO);

        // 校验累计产值不超过合同金额（含变更）
        BigDecimal newCumulativeOutput = (contract.getCumulativeOutput() != null ? contract.getCumulativeOutput() : BigDecimal.ZERO)
                .add(report.getCurrentOutput());
        if (newCumulativeOutput.compareTo(maxOutput) > 0) {
            throw new BusinessException("累计产值不能超过合同金额（含变更），当前上限：" + maxOutput);
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("currentOutput", report.getCurrentOutput());
        variables.put("projectId", report.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "OUTPUT_REPORT", id, "output_report_approval", variables);

        report.setWorkflowInstanceId(processInstanceId);
        report.setStatus("APPROVED");
        report.setCumulativeOutput(newCumulativeOutput);
        outputReportMapper.updateById(report);

        // 回写合同累计产值
        contract.setCumulativeOutput(newCumulativeOutput);
        contractMapper.updateById(contract);

        // 回写项目累计产值
        BizProject project = projectMapper.selectById(report.getProjectId());
        if (project != null) {
            BigDecimal projectCumulativeOutput = (project.getCumulativeOutput() != null ? project.getCumulativeOutput() : BigDecimal.ZERO)
                    .add(report.getCurrentOutput());
            project.setCumulativeOutput(projectCumulativeOutput);
            projectMapper.updateById(project);
        }
    }
}
