package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.domain.BizRetentionReturn;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import com.zwinsight.finance.mapper.BizRetentionReturnMapper;
import com.zwinsight.finance.task.RetentionWarningTask;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 质保金返还服务
 */
@Service
@RequiredArgsConstructor
public class RetentionReturnService {

    private final BizRetentionReturnMapper retentionReturnMapper;
    private final BizRetentionMoneyMapper retentionMoneyMapper;
    private final ApprovalService approvalService;
    @Lazy
    private final RetentionWarningTask retentionWarningTask;

    /**
     * 新增返还申请
     */
    public void save(BizRetentionReturn retentionReturn) {
        retentionReturn.setStatus("DRAFT");
        retentionReturnMapper.insert(retentionReturn);
    }

    /**
     * 提交返还申请（审批通过→更新returnedAmount，若全部返还则标记RETURNED）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizRetentionReturn retentionReturn = retentionReturnMapper.selectById(id);
        if (retentionReturn == null) {
            throw new BusinessException("返还记录不存在");
        }
        if (!"DRAFT".equals(retentionReturn.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        BizRetentionMoney retentionMoney = retentionMoneyMapper.selectById(retentionReturn.getRetentionId());
        if (retentionMoney == null) {
            throw new BusinessException("关联质保金记录不存在");
        }

        // 校验返还金额（P0 修复 FIN-RTR-08，2026-08-12：retentionAmount/returnAmount null 时
        // 原实现 NPE，且返还额必须>0）
        if (retentionReturn.getReturnAmount() == null || retentionReturn.getReturnAmount().signum() <= 0) {
            throw new BusinessException("返还金额必须大于0");
        }
        BigDecimal retentionAmount = retentionMoney.getRetentionAmount() != null
                ? retentionMoney.getRetentionAmount() : BigDecimal.ZERO;
        BigDecimal returnedAmount = retentionMoney.getReturnedAmount() != null
                ? retentionMoney.getReturnedAmount() : BigDecimal.ZERO;
        BigDecimal maxReturn = retentionAmount.subtract(returnedAmount);
        if (retentionReturn.getReturnAmount().compareTo(maxReturn) > 0) {
            throw new BusinessException("返还金额不能超过剩余可返还金额：" + maxReturn);
        }

        // 发起审批
        Map<String, Object> variables = new HashMap<>();
        variables.put("returnAmount", retentionReturn.getReturnAmount());
        variables.put("retentionId", retentionReturn.getRetentionId());
        String processInstanceId = approvalService.startProcess(
                "RETENTION_RETURN", id, "retention_return_approval", variables);

        retentionReturn.setWorkflowInstanceId(processInstanceId);
        retentionReturn.setStatus("APPROVED");
        retentionReturnMapper.updateById(retentionReturn);

        // 更新质保金已返还金额
        retentionMoney.setReturnedAmount(returnedAmount.add(retentionReturn.getReturnAmount()));

        // 判断是否全部返还
        if (retentionMoney.getReturnedAmount().compareTo(retentionAmount) >= 0) {
            retentionMoney.setStatus("RETURNED");
        }
        retentionMoneyMapper.updateById(retentionMoney);

        // P0 修复（FIN-RTR-07，2026-08-12）：全额退还置 RETURNED 后联动清理预警去重 key，
        // 原实现未调用导致退还后预警任务仍可能重复发送
        if ("RETURNED".equals(retentionMoney.getStatus())) {
            retentionWarningTask.onRetentionReturned(retentionMoney.getId());
        }
    }
}
