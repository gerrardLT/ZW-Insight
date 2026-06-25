package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizEntryApply;
import com.zwinsight.hr.mapper.BizEntryApplyMapper;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.system.service.SysUserService;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 入职申请服务
 */
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
        entryApplyMapper.updateById(apply);
    }

    /**
     * 删除入职申请
     */
    public void delete(Long id) {
        BizEntryApply existing = entryApplyMapper.selectById(id);
        if (existing == null) throw new BusinessException("入职申请不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        entryApplyMapper.deleteById(id);
    }

    /**
     * 提交入职申请（审批通过→自动创建系统账号）
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
        apply.setStatus("APPROVED");
        entryApplyMapper.updateById(apply);

        // 自动创建系统账号
        SysUser user = new SysUser();
        user.setUsername(apply.getUsername());
        user.setPassword("123456"); // 默认密码，由SysUserService加密
        user.setRealName(apply.getRealName());
        user.setPhone(apply.getPhone());
        user.setOrgId(apply.getOrgId());
        user.setPostId(apply.getPostId());
        user.setStatus(1);
        sysUserService.save(user);
    }
}
