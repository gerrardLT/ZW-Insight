package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizRegularApply;
import com.zwinsight.hr.mapper.BizRegularApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 转正申请服务
 */
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
     * 提交转正申请（审批→更新员工档案）
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
        String processInstanceId = approvalService.startProcess(
                "REGULAR_APPLY", id, "regular_apply_approval", variables);

        apply.setStatus("APPROVED");
        regularApplyMapper.updateById(apply);
    }
}
