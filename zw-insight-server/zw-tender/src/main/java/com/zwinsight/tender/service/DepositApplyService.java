package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
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

    /**
     * 分页查询
     */
    public PageResult<BizDepositApply> page(int page, int size, Long projectId) {
        Page<BizDepositApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizDepositApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizDepositApply::getProjectId, projectId)
                .orderByDesc(BizDepositApply::getCreatedAt);
        Page<BizDepositApply> result = depositApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增保证金申请
     */
    public void save(BizDepositApply apply) {
        apply.setStatus("DRAFT");
        depositApplyMapper.insert(apply);
    }

    /**
     * 提交保证金申请（审批→确认付款）
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
        apply.setStatus("PAID");
        depositApplyMapper.updateById(apply);
    }
}
