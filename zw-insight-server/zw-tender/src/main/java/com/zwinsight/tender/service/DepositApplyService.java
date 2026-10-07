package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizDepositApply;
import com.zwinsight.tender.mapper.BizDepositApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 保证金申请服务
 */
@Service
@RequiredArgsConstructor
public class DepositApplyService {

    private final BizDepositApplyMapper depositApplyMapper;
    private final ApprovalService approvalService;
    private final com.zwinsight.project.mapper.BizProjectMapper projectMapper;

    /**
     * 分页查询（支持按 projectId 与 registerId 组合检索）
     */
    public PageResult<BizDepositApply> page(int page, int size, Long projectId) {
        return page(page, size, projectId, null);
    }

    public PageResult<BizDepositApply> page(int page, int size, Long projectId, Long registerId) {
        Page<BizDepositApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizDepositApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizDepositApply::getProjectId, projectId)
                .eq(registerId != null, BizDepositApply::getRegisterId, registerId)
                .orderByDesc(BizDepositApply::getCreatedAt);
        Page<BizDepositApply> result = depositApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增保证金申请
     */
    public void save(BizDepositApply apply) {
        // TI-1 法定保证金上限守卫（P1-M2 V2026_82）：不得超过项目预算金额的 2%
        if (apply.getProjectId() != null && apply.getDepositAmount() != null) {
            com.zwinsight.project.domain.BizProject project = projectMapper.selectById(apply.getProjectId());
            if (project != null && project.getBudgetAmount() != null
                    && project.getBudgetAmount().signum() > 0) {
                java.math.BigDecimal cap = project.getBudgetAmount()
                        .multiply(new java.math.BigDecimal("0.02"));
                if (apply.getDepositAmount().compareTo(cap) > 0) {
                    throw new com.zwinsight.common.exception.BusinessException(String.format(
                            "投标保证金（%s）超过项目预算金额 2%% 法定上限（%s），请调整",
                            apply.getDepositAmount().toPlainString(), cap.toPlainString()));
                }
            }
        }
        apply.setStatus("DRAFT");
        depositApplyMapper.insert(apply);
    }

    /**
     * 更新保证金申请（P1 修复，2026-08-12 批次二取证枚举：原裸 updateById 致
     * PAID 申请的 depositAmount/status 可被 PUT 篡改，对齐费用模块 DRAFT 守卫）
     */
    public void update(BizDepositApply apply) {
        BizDepositApply existing = depositApplyMapper.selectById(apply.getId());
        if (existing == null) {
            throw new BusinessException("保证金申请不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        // 状态由提交/审批链路维护，置 null 防 PUT 体携带 status 篡改（PAID 金额单据高危）
        apply.setStatus(null);
        depositApplyMapper.updateById(apply);
    }

    /**
     * 删除保证金申请
     */
    public void delete(Long id) {
        BizDepositApply apply = depositApplyMapper.selectById(id);
        if (apply != null && !"DRAFT".equals(apply.getStatus()) && !E2eTestGuard.containsE2eTestMarker(apply)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        depositApplyMapper.deleteById(id);
    }

    /**
     * 提交保证金申请（审批→确认付款）
     * <p>P1 修复（2026-08-12 批次二取证枚举）：原提交即置 PAID（未等审批），且无驳回回调，
     * 驳回后单据永久停留 PAID。改为 SUBMITTED 中间态，审批通过由
     * DepositApplyApprovalListener 置 PAID，驳回/撤回回退 DRAFT。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizDepositApply apply = depositApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("保证金申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("depositAmount", apply.getDepositAmount());
        variables.put("projectId", apply.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "DEPOSIT_APPLY", id, "deposit_apply_approval", variables);

        apply.setWorkflowInstanceId(processInstanceId);
        apply.setStatus("SUBMITTED");
        depositApplyMapper.updateById(apply);
    }

    /**
     * 审批通过回调：置 PAID（确认付款），幂等短路防重复事件
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizDepositApply apply = depositApplyMapper.selectById(id);
        if (apply == null || !"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("PAID");
        depositApplyMapper.updateById(apply);
    }

    /**
     * 审批驳回/撤回回调：回退 DRAFT（未付款无资金回冲）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizDepositApply apply = depositApplyMapper.selectById(id);
        if (apply == null || !"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("DRAFT");
        depositApplyMapper.updateById(apply);
    }
}
