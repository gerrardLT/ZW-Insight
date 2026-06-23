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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 离职申请服务
 */
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
     * 提交离职申请（审批→停用账号）
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

        apply.setStatus("APPROVED");
        resignApplyMapper.updateById(apply);

        // 停用账号
        SysUser user = userMapper.selectById(apply.getUserId());
        if (user != null) {
            user.setStatus(0);
            userMapper.updateById(user);
        }
    }
}
