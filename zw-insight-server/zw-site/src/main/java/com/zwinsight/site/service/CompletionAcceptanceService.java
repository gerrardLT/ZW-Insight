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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 竣工验收服务
 */
@Slf4j
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
     * 提交竣工验收（DRAFT→SUBMITTED 中间态，审批通过后由 onApproved 置项目 COMPLETED）
     * <p>
     * P1 修复（2026-08-13，批次三取证枚举）：原实现提交即置 APPROVED 且未等审批
     * 直接将项目置 COMPLETED，审批驳回后项目状态无法回退。改为审批后生效模式。
     * </p>
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
        acceptance.setStatus("SUBMITTED");
        acceptanceMapper.updateById(acceptance);
    }

    /**
     * 审批通过回调：SUBMITTED→APPROVED 并置项目 COMPLETED（幂等：非 SUBMITTED 跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizCompletionAcceptance acceptance = acceptanceMapper.selectById(id);
        if (acceptance == null) {
            log.warn("竣工验收审批通过回调：记录不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(acceptance.getStatus())) {
            log.info("竣工验收审批通过回调：非待审批状态跳过, id={}, status={}", id, acceptance.getStatus());
            return;
        }
        acceptance.setStatus("APPROVED");
        acceptanceMapper.updateById(acceptance);

        // 更新项目状态为COMPLETED
        BizProject project = projectMapper.selectById(acceptance.getProjectId());
        if (project != null) {
            project.setStatus("COMPLETED");
            projectMapper.updateById(project);
        } else {
            log.warn("竣工验收审批通过：项目不存在, projectId={}", acceptance.getProjectId());
        }
        log.info("竣工验收审批通过，项目已竣工: acceptanceId={}, projectId={}", id, acceptance.getProjectId());
    }

    /**
     * 审批驳回回调：SUBMITTED→DRAFT（幂等：非 SUBMITTED 跳过）
     */
    public void onRejected(Long id) {
        BizCompletionAcceptance acceptance = acceptanceMapper.selectById(id);
        if (acceptance == null || !"SUBMITTED".equals(acceptance.getStatus())) {
            return;
        }
        acceptance.setStatus("DRAFT");
        acceptanceMapper.updateById(acceptance);
        log.info("竣工验收审批驳回，记录回退草稿: acceptanceId={}", id);
    }
}
