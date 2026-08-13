package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizSealApply;
import com.zwinsight.hr.mapper.BizSealApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 用印申请服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SealApplyService {

    private final BizSealApplyMapper sealApplyMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizSealApply> page(int page, int size) {
        Page<BizSealApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSealApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BizSealApply::getCreatedAt);
        Page<BizSealApply> result = sealApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增用印申请
     */
    public void save(BizSealApply apply) {
        apply.setStatus("DRAFT");
        sealApplyMapper.insert(apply);
    }

    /**
     * 更新用印申请（仅 DRAFT；P1 修复：原实现无状态守卫且可经 PUT 体篡改 status）
     */
    public void update(BizSealApply apply) {
        BizSealApply existing = sealApplyMapper.selectById(apply.getId());
        if (existing == null) throw new BusinessException("用印申请不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        // 防 PUT 体携带 status 直接落库绕过审批（MP NOT_NULL 策略置 null 不落库）
        apply.setStatus(null);
        sealApplyMapper.updateById(apply);
    }

    /**
     * 删除用印申请
     */
    public void delete(Long id) {
        BizSealApply apply = sealApplyMapper.selectById(id);
        if (apply != null && !"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        sealApplyMapper.deleteById(id);
    }

    /**
     * 提交用印申请（DRAFT→SUBMITTED 中间态，审批通过后由 onApproved 置 APPROVED）
     * <p>
     * P1 修复（2026-08-13，批次三取证枚举）：原实现提交即置 APPROVED，审批驳回后
     * 单据永久停留 APPROVED。改为审批后生效模式。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizSealApply apply = sealApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("用印申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("sealType", apply.getSealType());
        variables.put("applicant", apply.getApplicant());
        approvalService.startProcess(
                "SEAL_APPLY", id, "seal_apply_approval", variables);

        apply.setStatus("SUBMITTED");
        sealApplyMapper.updateById(apply);
    }

    /**
     * 审批通过回调：SUBMITTED→APPROVED（幂等：非 SUBMITTED 跳过）
     */
    public void onApproved(Long id) {
        BizSealApply apply = sealApplyMapper.selectById(id);
        if (apply == null || !"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("APPROVED");
        sealApplyMapper.updateById(apply);
        log.info("用印审批通过: applyId={}", id);
    }

    /**
     * 审批驳回回调：SUBMITTED→DRAFT（幂等：非 SUBMITTED 跳过）
     */
    public void onRejected(Long id) {
        BizSealApply apply = sealApplyMapper.selectById(id);
        if (apply == null || !"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("DRAFT");
        sealApplyMapper.updateById(apply);
        log.info("用印审批驳回，申请回退草稿: applyId={}", id);
    }
}
