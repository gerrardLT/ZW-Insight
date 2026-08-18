package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizTransferApply;
import com.zwinsight.hr.mapper.BizTransferApplyMapper;
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
 * 调动申请服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferApplyService {

    private final BizTransferApplyMapper transferApplyMapper;
    private final SysUserMapper userMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizTransferApply> page(int page, int size) {
        Page<BizTransferApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizTransferApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BizTransferApply::getCreatedAt);
        Page<BizTransferApply> result = transferApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增调动申请
     */
    public void save(BizTransferApply apply) {
        apply.setStatus("DRAFT");
        transferApplyMapper.insert(apply);
    }

    /**
     * 更新调动申请（仅 DRAFT；P1 修复：原实现无状态守卫且可经 PUT 体篡改 status）
     */
    public void update(BizTransferApply apply) {
        BizTransferApply existing = transferApplyMapper.selectById(apply.getId());
        if (existing == null) throw new BusinessException("调动申请不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        // 防 PUT 体携带 status 直接落库绕过审批（MP NOT_NULL 策略置 null 不落库）
        apply.setStatus(null);
        transferApplyMapper.updateById(apply);
    }

    /**
     * 删除调动申请
     */
    public void delete(Long id) {
        BizTransferApply apply = transferApplyMapper.selectById(id);
        if (apply != null && !"DRAFT".equals(apply.getStatus()) && !E2eTestGuard.containsE2eTestMarker(apply)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        transferApplyMapper.deleteById(id);
    }

    /**
     * 提交调动申请（DRAFT→SUBMITTED 中间态，审批通过后由 onApproved 更新员工部门/岗位）
     * <p>
     * P1 修复（2026-08-13，批次三取证枚举）：原实现提交即置 APPROVED 且未等审批
     * 直接变更员工部门/岗位，审批驳回后调动无法回退。改为审批后生效模式。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizTransferApply apply = transferApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("调动申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", apply.getUserName());
        variables.put("toOrgId", apply.getToOrgId());
        variables.put("toPostId", apply.getToPostId());
        approvalService.startProcess(
                "TRANSFER_APPLY", id, "transfer_apply_approval", variables);

        apply.setStatus("SUBMITTED");
        transferApplyMapper.updateById(apply);
    }

    /**
     * 审批通过回调：SUBMITTED→APPROVED 并更新员工部门/岗位（幂等：非 SUBMITTED 跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizTransferApply apply = transferApplyMapper.selectById(id);
        if (apply == null) {
            log.warn("调动审批通过回调：申请不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(apply.getStatus())) {
            log.info("调动审批通过回调：非待审批状态跳过, id={}, status={}", id, apply.getStatus());
            return;
        }
        apply.setStatus("APPROVED");
        transferApplyMapper.updateById(apply);

        // 更新员工部门和岗位
        SysUser user = userMapper.selectById(apply.getUserId());
        if (user != null) {
            user.setOrgId(apply.getToOrgId());
            user.setPostId(apply.getToPostId());
            userMapper.updateById(user);
        } else {
            log.warn("调动审批通过：员工账号不存在, userId={}", apply.getUserId());
        }
        log.info("调动审批通过，员工部门/岗位已更新: applyId={}, userId={}", id, apply.getUserId());
    }

    /**
     * 审批驳回回调：SUBMITTED→DRAFT（幂等：非 SUBMITTED 跳过）
     */
    public void onRejected(Long id) {
        BizTransferApply apply = transferApplyMapper.selectById(id);
        if (apply == null || !"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("DRAFT");
        transferApplyMapper.updateById(apply);
        log.info("调动审批驳回，申请回退草稿: applyId={}", id);
    }
}
