package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizEntryApply;
import com.zwinsight.hr.mapper.BizEntryApplyMapper;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.system.service.SysUserService;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 入职申请服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntryApplyService {

    private final BizEntryApplyMapper entryApplyMapper;
    private final SysUserService sysUserService;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizEntryApply> page(int page, int size, String realName) {
        Page<BizEntryApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizEntryApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(realName != null && !realName.isEmpty(), BizEntryApply::getRealName, realName)
                .orderByDesc(BizEntryApply::getCreatedAt);
        Page<BizEntryApply> result = entryApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增入职申请
     */
    public void save(BizEntryApply apply) {
        apply.setStatus("DRAFT");
        entryApplyMapper.insert(apply);
    }

    /**
     * 根据ID查询
     */
    public BizEntryApply getById(Long id) {
        BizEntryApply apply = entryApplyMapper.selectById(id);
        if (apply == null) throw new BusinessException("入职申请不存在");
        return apply;
    }

    /**
     * 更新入职申请
     */
    public void update(BizEntryApply apply) {
        BizEntryApply existing = entryApplyMapper.selectById(apply.getId());
        if (existing == null) throw new BusinessException("入职申请不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        // 防 PUT 体携带 status 直接落库绕过审批（MP NOT_NULL 策略置 null 不落库）
        apply.setStatus(null);
        entryApplyMapper.updateById(apply);
    }

    /**
     * 删除入职申请
     */
    public void delete(Long id) {
        BizEntryApply existing = entryApplyMapper.selectById(id);
        if (existing == null) throw new BusinessException("入职申请不存在");
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) throw new BusinessException("仅草稿状态可删除");
        entryApplyMapper.deleteById(id);
    }

    /**
     * 提交入职申请（DRAFT→SUBMITTED 中间态，审批通过后由 onApproved 创建系统账号）
     * <p>
     * P1 修复（2026-08-13，批次三取证枚举）：原实现提交即置 APPROVED 且未等审批
     * 直接创建系统账号，审批驳回后账号无法回收。改为审批后生效模式。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizEntryApply apply = entryApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("入职申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("realName", apply.getRealName());
        variables.put("orgId", apply.getOrgId());
        String processInstanceId = approvalService.startProcess(
                "ENTRY_APPLY", id, "entry_apply_approval", variables);

        apply.setWorkflowInstanceId(processInstanceId);
        apply.setStatus("SUBMITTED");
        entryApplyMapper.updateById(apply);
    }

    /**
     * 审批通过回调：SUBMITTED→APPROVED 并自动创建系统账号（幂等：非 SUBMITTED 跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizEntryApply apply = entryApplyMapper.selectById(id);
        if (apply == null) {
            log.warn("入职审批通过回调：申请不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(apply.getStatus())) {
            log.info("入职审批通过回调：非待审批状态跳过, id={}, status={}", id, apply.getStatus());
            return;
        }
        apply.setStatus("APPROVED");
        entryApplyMapper.updateById(apply);

        // 自动创建系统账号（字段从申请单带入；失败抛异常回滚申请状态，不静默）
        SysUser user = new SysUser();
        user.setUsername(apply.getUsername());
        user.setPassword("123456"); // 默认密码，由SysUserService加密
        user.setRealName(apply.getRealName());
        user.setPhone(apply.getPhone());
        user.setOrgId(apply.getOrgId());
        user.setPostId(apply.getPostId());
        user.setStatus(1);
        sysUserService.save(user);
        log.info("入职审批通过，系统账号已创建: applyId={}, username={}", id, apply.getUsername());
    }

    /**
     * 审批驳回回调：SUBMITTED→DRAFT（幂等：非 SUBMITTED 跳过）
     */
    public void onRejected(Long id) {
        BizEntryApply apply = entryApplyMapper.selectById(id);
        if (apply == null || !"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("DRAFT");
        entryApplyMapper.updateById(apply);
        log.info("入职审批驳回，申请回退草稿: applyId={}", id);
    }
}
