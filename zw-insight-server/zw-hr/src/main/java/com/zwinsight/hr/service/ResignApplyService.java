package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizResignApply;
import com.zwinsight.hr.mapper.BizResignApplyMapper;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 离职申请服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResignApplyService {

    private final BizResignApplyMapper resignApplyMapper;
    private final SysUserMapper userMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizResignApply> page(int page, int size) {
        Page<BizResignApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizResignApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BizResignApply::getCreatedAt);
        Page<BizResignApply> result = resignApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增离职申请
     */
    public void save(BizResignApply apply) {
        apply.setStatus("DRAFT");
        resignApplyMapper.insert(apply);
    }

    /**
     * 提交离职申请（DRAFT→SUBMITTED 中间态，审批通过后由 onApproved 停用账号）
     * <p>
     * P1 修复（2026-08-13，批次三取证枚举）：原实现提交即置 APPROVED 且未等审批
     * 直接停用员工账号，审批驳回后员工已被停用无法恢复。改为审批后生效模式。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizResignApply apply = resignApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("离职申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", apply.getUserName());
        variables.put("userId", apply.getUserId());
        String processInstanceId = approvalService.startProcess(
                "RESIGN_APPLY", id, "resign_apply_approval", variables);

        apply.setWorkflowInstanceId(processInstanceId);
        apply.setStatus("SUBMITTED");
        if (resignApplyMapper.updateById(apply) != 1) {
            throw new BusinessException("审批实例回写失败，请刷新后重试");
        }
    }

    /**
     * 审批通过回调：SUBMITTED→APPROVED 并停用账号（幂等：非 SUBMITTED 跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizResignApply apply = resignApplyMapper.selectById(id);
        if (apply == null) {
            log.warn("离职审批通过回调：申请不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(apply.getStatus())) {
            log.info("离职审批通过回调：非待审批状态跳过, id={}, status={}", id, apply.getStatus());
            return;
        }
        // 先校验账号再落状态：账号不存在必须失败回滚，不能记个 warn 就把申请置 APPROVED
        // （原实现会出现"离职已批准但账号仍可登录"的静默失效）
        SysUser user = userMapper.selectById(apply.getUserId());
        if (user == null) {
            throw new BusinessException("离职员工账号不存在，离职申请无法生效");
        }
        if (apply.getTenantId() != null && user.getTenantId() != null
                && !apply.getTenantId().equals(user.getTenantId())) {
            throw new BusinessException("员工账号不属于申请所在租户，离职申请无法生效");
        }
        // 状态 CAS：并发重复回调只有一次生效
        int moved = resignApplyMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizResignApply>()
                .eq(BizResignApply::getId, id)
                .eq(BizResignApply::getStatus, "SUBMITTED")
                .set(BizResignApply::getStatus, "APPROVED"));
        if (moved != 1) {
            log.info("离职审批通过回调：并发回调已处理, id={}", id);
            return;
        }
        apply.setStatus("APPROVED");

        user.setStatus(0);
        userMapper.updateById(user);
        log.info("离职审批通过，账号已停用: applyId={}, userId={}", id, apply.getUserId());
    }

    /**
     * 审批驳回回调：SUBMITTED→DRAFT（幂等：非 SUBMITTED 跳过）
     */
    public void onRejected(Long id) {
        BizResignApply apply = resignApplyMapper.selectById(id);
        if (apply == null || !"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("DRAFT");
        resignApplyMapper.updateById(apply);
        log.info("离职审批驳回，申请回退草稿: applyId={}", id);
    }
}
