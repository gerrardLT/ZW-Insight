package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizReserveFundApply;
import com.zwinsight.finance.mapper.BizReserveFundApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 备用金申请服务
 */
@Service
@RequiredArgsConstructor
public class ReserveFundApplyService {

    private final BizReserveFundApplyMapper reserveFundApplyMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizReserveFundApply> page(int page, int size, Long projectId) {
        Page<BizReserveFundApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizReserveFundApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizReserveFundApply::getProjectId, projectId)
                .orderByDesc(BizReserveFundApply::getCreatedAt);
        Page<BizReserveFundApply> result = reserveFundApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增备用金申请
     */
    public void save(BizReserveFundApply apply) {
        apply.setStatus("DRAFT");
        if (apply.getReturnedAmount() == null) {
            apply.setReturnedAmount(BigDecimal.ZERO);
        }
        if (apply.getOffsetAmount() == null) {
            apply.setOffsetAmount(BigDecimal.ZERO);
        }
        reserveFundApplyMapper.insert(apply);
    }

    /**
     * 提交备用金申请
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizReserveFundApply apply = reserveFundApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("备用金申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("applyAmount", apply.getApplyAmount());
        variables.put("projectId", apply.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "RESERVE_FUND_APPLY", id, "reserve_fund_apply_approval", variables);

        apply.setWorkflowInstanceId(processInstanceId);
        apply.setStatus("APPROVED");
        reserveFundApplyMapper.updateById(apply);
    }
}
