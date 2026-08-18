package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizRegularApply;
import com.zwinsight.hr.mapper.BizRegularApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 转正申请服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegularApplyService {

    private final BizRegularApplyMapper regularApplyMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizRegularApply> page(int page, int size) {
        Page<BizRegularApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizRegularApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BizRegularApply::getCreatedAt);
        Page<BizRegularApply> result = regularApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增转正申请
     */
    public void save(BizRegularApply apply) {
        apply.setStatus("DRAFT");
        regularApplyMapper.insert(apply);
    }

    /**
     * 更新转正申请（仅 DRAFT；P1 修复：原实现无状态守卫且可经 PUT 体篡改 status）
     */
    public void update(BizRegularApply apply) {
        BizRegularApply existing = regularApplyMapper.selectById(apply.getId());
        if (existing == null) throw new BusinessException("转正申请不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        // 防 PUT 体携带 status 直接落库绕过审批（MP NOT_NULL 策略置 null 不落库）
        apply.setStatus(null);
        regularApplyMapper.updateById(apply);
    }

    /**
     * 删除转正申请
     */
    public void delete(Long id) {
        BizRegularApply apply = regularApplyMapper.selectById(id);
        if (apply != null && !"DRAFT".equals(apply.getStatus()) && !E2eTestGuard.containsE2eTestMarker(apply)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        regularApplyMapper.deleteById(id);
    }

    /**
     * 提交转正申请（DRAFT→SUBMITTED 中间态，审批通过后由 onApproved 置 APPROVED）
     * <p>
     * P1 修复（2026-08-13，批次三取证枚举）：原实现提交即置 APPROVED，审批驳回后
     * 单据永久停留 APPROVED。改为审批后生效模式。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizRegularApply apply = regularApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("转正申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", apply.getUserName());
        variables.put("userId", apply.getUserId());
        approvalService.startProcess(
                "REGULAR_APPLY", id, "regular_apply_approval", variables);

        apply.setStatus("SUBMITTED");
        regularApplyMapper.updateById(apply);
    }

    /**
     * 审批通过回调：SUBMITTED→APPROVED（幂等：非 SUBMITTED 跳过）
     */
    public void onApproved(Long id) {
        BizRegularApply apply = regularApplyMapper.selectById(id);
        if (apply == null || !"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("APPROVED");
        regularApplyMapper.updateById(apply);
        log.info("转正审批通过: applyId={}", id);
    }

    /**
     * 审批驳回回调：SUBMITTED→DRAFT（幂等：非 SUBMITTED 跳过）
     */
    public void onRejected(Long id) {
        BizRegularApply apply = regularApplyMapper.selectById(id);
        if (apply == null || !"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("DRAFT");
        regularApplyMapper.updateById(apply);
        log.info("转正审批驳回，申请回退草稿: applyId={}", id);
    }
}
