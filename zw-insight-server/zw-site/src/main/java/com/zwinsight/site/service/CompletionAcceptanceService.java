package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.site.domain.BizCompletionAcceptance;
import com.zwinsight.site.mapper.BizCompletionAcceptanceMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 竣工验收服务
 */
@Service
@RequiredArgsConstructor
public class CompletionAcceptanceService {

    private final BizCompletionAcceptanceMapper acceptanceMapper;
    private final BizProjectMapper projectMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizCompletionAcceptance> page(int page, int size, Long projectId) {
        Page<BizCompletionAcceptance> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizCompletionAcceptance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizCompletionAcceptance::getProjectId, projectId)
                .orderByDesc(BizCompletionAcceptance::getCreatedAt);
        Page<BizCompletionAcceptance> result = acceptanceMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增竣工验收
     */
    public void save(BizCompletionAcceptance acceptance) {
        acceptance.setStatus("DRAFT");
        acceptanceMapper.insert(acceptance);
    }

    /**
     * 提交竣工验收（审批通过→更新项目状态为COMPLETED）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizCompletionAcceptance acceptance = acceptanceMapper.selectById(id);
        if (acceptance == null) {
            throw new BusinessException("竣工验收记录不存在");
        }
        if (!"DRAFT".equals(acceptance.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("projectId", acceptance.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "COMPLETION_ACCEPTANCE", id, "completion_acceptance_approval", variables);

        acceptance.setWorkflowInstanceId(processInstanceId);
        acceptance.setStatus("APPROVED");
        acceptanceMapper.updateById(acceptance);

        // 更新项目状态为COMPLETED
        BizProject project = projectMapper.selectById(acceptance.getProjectId());
        if (project != null) {
            project.setStatus("COMPLETED");
            projectMapper.updateById(project);
        }
    }
}
