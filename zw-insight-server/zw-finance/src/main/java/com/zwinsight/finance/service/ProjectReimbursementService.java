package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizProjectReimbursement;
import com.zwinsight.finance.domain.BizReserveFundApply;
import com.zwinsight.finance.mapper.BizProjectReimbursementMapper;
import com.zwinsight.finance.mapper.BizReserveFundApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 项目报销服务
 */
@Service
@RequiredArgsConstructor
public class ProjectReimbursementService {

    private final BizProjectReimbursementMapper reimbursementMapper;
    private final BizReserveFundApplyMapper reserveFundApplyMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizProjectReimbursement> page(int page, int size, Long projectId) {
        Page<BizProjectReimbursement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizProjectReimbursement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizProjectReimbursement::getProjectId, projectId)
                .orderByDesc(BizProjectReimbursement::getCreatedAt);
        Page<BizProjectReimbursement> result = reimbursementMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增项目报销
     */
    public void save(BizProjectReimbursement reimbursement) {
        reimbursement.setStatus("DRAFT");
        reimbursementMapper.insert(reimbursement);
    }

    /**
     * 提交项目报销（扣减间接费用预算，冲抵备用金）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizProjectReimbursement reimbursement = reimbursementMapper.selectById(id);
        if (reimbursement == null) {
            throw new BusinessException("报销记录不存在");
        }
        if (!"DRAFT".equals(reimbursement.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        // P0 修复（FIN-PRJ-06，2026-08-12）：报销金额必须>0，原实现负/零无校验直接生效
        if (reimbursement.getTotalAmount() == null || reimbursement.getTotalAmount().signum() <= 0) {
            throw new BusinessException("报销金额必须大于0");
        }

        // 发起审批
        Map<String, Object> variables = new HashMap<>();
        variables.put("totalAmount", reimbursement.getTotalAmount());
        variables.put("projectId", reimbursement.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "PROJECT_REIMBURSEMENT", id, "project_reimbursement_approval", variables);

        reimbursement.setWorkflowInstanceId(processInstanceId);
        reimbursement.setStatus("APPROVED");
        reimbursementMapper.updateById(reimbursement);

        // 冲抵备用金
        if (reimbursement.getOffsetReserve() != null && reimbursement.getOffsetReserve() == 1
                && reimbursement.getReserveApplyId() != null) {
            BizReserveFundApply reserveApply = reserveFundApplyMapper.selectById(reimbursement.getReserveApplyId());
            if (reserveApply != null) {
                BigDecimal currentOffset = reserveApply.getOffsetAmount() == null
                        ? BigDecimal.ZERO : reserveApply.getOffsetAmount();
                BigDecimal offsetAmount = reimbursement.getOffsetAmount() == null
                        ? BigDecimal.ZERO : reimbursement.getOffsetAmount();
                // P0 修复（FIN-PRJ-07，2026-08-12）：冲抵额必须≥0、不超报销额、
                // 且不超备用金待冲抵余额（申请-已还-已冲抵），原实现可超额冲抵
                if (offsetAmount.signum() < 0) {
                    throw new BusinessException("冲抵金额不能为负数");
                }
                if (offsetAmount.compareTo(reimbursement.getTotalAmount()) > 0) {
                    throw new BusinessException("冲抵金额不能超过报销金额");
                }
                BigDecimal applyAmount = reserveApply.getApplyAmount() == null
                        ? BigDecimal.ZERO : reserveApply.getApplyAmount();
                BigDecimal returnedAmount = reserveApply.getReturnedAmount() == null
                        ? BigDecimal.ZERO : reserveApply.getReturnedAmount();
                BigDecimal pendingOffset = applyAmount.subtract(returnedAmount).subtract(currentOffset);
                if (offsetAmount.compareTo(pendingOffset) > 0) {
                    throw new BusinessException("冲抵金额超过备用金待冲抵余额：" + pendingOffset);
                }
                reserveApply.setOffsetAmount(currentOffset.add(offsetAmount));
                reserveFundApplyMapper.updateById(reserveApply);
            }
        }
    }
}
