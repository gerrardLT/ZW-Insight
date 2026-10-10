package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.domain.BizRetentionReturn;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import com.zwinsight.finance.mapper.BizRetentionReturnMapper;
import com.zwinsight.finance.task.RetentionWarningTask;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 质保金返还服务
 */
@Slf4j
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
     * 提交返还申请（发起审批，状态置 SUBMITTED；质保金已返还金额待审批通过后回写）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizRetentionReturn retentionReturn = retentionReturnMapper.selectById(id);
        if (retentionReturn == null) {
            throw new BusinessException("返还记录不存在");
        }
        if (!"DRAFT".equals(retentionReturn.getStatus()) && !"REJECTED".equals(retentionReturn.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可提交");
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
        validateReturnAmount(retentionReturn, retentionMoney);

        // 发起审批
        Map<String, Object> variables = new HashMap<>();
        variables.put("returnAmount", retentionReturn.getReturnAmount());
        variables.put("retentionId", retentionReturn.getRetentionId());
        String processInstanceId = approvalService.startProcess(
                "RETENTION_RETURN", id, "retention_return_approval", variables);

        retentionReturn.setWorkflowInstanceId(processInstanceId);
        retentionReturn.setStatus("SUBMITTED");
        retentionReturnMapper.updateById(retentionReturn);
    }

    /**
     * 审批通过回调：置 APPROVED 并回写质保金已返还金额
     * <p>幂等：状态已为 APPROVED 时直接返回（兼容审批时点改造前的存量在途单据与重复事件），
     * 避免同一笔返还重复累加。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizRetentionReturn retentionReturn = retentionReturnMapper.selectById(id);
        if (retentionReturn == null) {
            log.warn("质保金返还审批通过回调：记录不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(retentionReturn.getStatus())) {
            log.info("质保金返还已生效，跳过重复回调, id={}", id);
            return;
        }

        BizRetentionMoney retentionMoney = retentionMoneyMapper.selectById(retentionReturn.getRetentionId());
        if (retentionMoney == null) {
            throw new BusinessException("关联质保金记录不存在");
        }
        // 审批期间可能已有其他返还单生效，生效前重新校验剩余可返还金额
        validateReturnAmount(retentionReturn, retentionMoney);

        retentionReturn.setStatus("APPROVED");
        retentionReturnMapper.updateById(retentionReturn);

        BigDecimal retentionAmount = retentionMoney.getRetentionAmount() != null
                ? retentionMoney.getRetentionAmount() : BigDecimal.ZERO;
        BigDecimal returnedAmount = retentionMoney.getReturnedAmount() != null
                ? retentionMoney.getReturnedAmount() : BigDecimal.ZERO;

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
        log.info("质保金返还审批通过并生效, id={}, returnAmount={}", id, retentionReturn.getReturnAmount());
    }

    /**
     * 审批驳回/撤回回调：状态置 REJECTED（质保金未回写，无需回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizRetentionReturn retentionReturn = retentionReturnMapper.selectById(id);
        if (retentionReturn == null) {
            log.warn("质保金返还驳回回调：记录不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(retentionReturn.getStatus())) {
            return;
        }
        retentionReturn.setStatus("REJECTED");
        retentionReturnMapper.updateById(retentionReturn);
        log.info("质保金返还审批驳回, id={}", id);
    }

    /** 返还金额不能超过剩余可返还金额（质保金金额 − 已返还） */
    private void validateReturnAmount(BizRetentionReturn retentionReturn, BizRetentionMoney retentionMoney) {
        BigDecimal retentionAmount = retentionMoney.getRetentionAmount() != null
                ? retentionMoney.getRetentionAmount() : BigDecimal.ZERO;
        BigDecimal returnedAmount = retentionMoney.getReturnedAmount() != null
                ? retentionMoney.getReturnedAmount() : BigDecimal.ZERO;
        BigDecimal maxReturn = retentionAmount.subtract(returnedAmount);
        if (retentionReturn.getReturnAmount().compareTo(maxReturn) > 0) {
            throw new BusinessException("返还金额不能超过剩余可返还金额：" + maxReturn);
        }
    }
}
