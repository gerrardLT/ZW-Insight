package com.zwinsight.labor.listener;

import com.zwinsight.labor.service.*;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 劳务模块审批回调监听器（P3-M6 A3 / LI-3）
 * <p>
 * 监听工作流审批完成与驳回事件，闭环驱动：
 * 1) LABOR_CONTRACT 劳务合同审批通过/驳回
 * 2) LABOR_OUTPUT 劳务产值审批通过/驳回（原子回写合同累计产值）
 * 3) LABOR_SETTLEMENT 劳务结算审批通过/驳回（原子回写合同累计结算）
 * 4) LABOR_PAYROLL 劳务工资单审批通过/驳回
 * 5) LABOR_REWARD_PUNISH 劳务奖惩审批通过/驳回
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LaborApprovalListener {

    public static final String TYPE_CONTRACT = "LABOR_CONTRACT";
    public static final String TYPE_OUTPUT = "LABOR_OUTPUT";
    public static final String TYPE_SETTLEMENT = "LABOR_SETTLEMENT";
    public static final String TYPE_PAYROLL = "LABOR_PAYROLL";
    public static final String TYPE_REWARD_PUNISH = "LABOR_REWARD_PUNISH";

    private final LaborContractService contractService;
    private final LaborOutputReportService outputReportService;
    private final LaborSettlementService settlementService;
    private final LaborPayrollService payrollService;
    private final LaborRewardPunishService rewardPunishService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        String businessType = event.getBusinessType();
        Long businessId = event.getBusinessId();

        if (TYPE_CONTRACT.equals(businessType)) {
            log.info("收到劳务合同审批通过事件: id={}, result={}", businessId, event.getResult());
            if ("APPROVED".equals(event.getResult())) {
                contractService.onApproved(businessId);
            }
        } else if (TYPE_OUTPUT.equals(businessType)) {
            log.info("收到劳务产值审批通过事件: id={}, result={}", businessId, event.getResult());
            if ("APPROVED".equals(event.getResult())) {
                outputReportService.onApproved(businessId);
            }
        } else if (TYPE_SETTLEMENT.equals(businessType)) {
            log.info("收到劳务结算审批通过事件: id={}, result={}", businessId, event.getResult());
            if ("APPROVED".equals(event.getResult())) {
                settlementService.onApproved(businessId);
            }
        } else if (TYPE_PAYROLL.equals(businessType)) {
            log.info("收到劳务工资单审批通过事件: id={}, result={}", businessId, event.getResult());
            if ("APPROVED".equals(event.getResult())) {
                payrollService.onApproved(businessId);
            }
        } else if (TYPE_REWARD_PUNISH.equals(businessType)) {
            log.info("收到劳务奖惩审批通过事件: id={}, result={}", businessId, event.getResult());
            if ("APPROVED".equals(event.getResult())) {
                rewardPunishService.onApproved(businessId);
            }
        }
    }

    /**
     * 审批驳回/撤回回调
     */
    @EventListener
    public void onApprovalReject(ApprovalRejectEvent event) {
        String bizType = event.getBizType();
        Long bizId = event.getBizId();

        if (TYPE_CONTRACT.equals(bizType)) {
            log.info("收到劳务合同审批驳回/撤回事件: id={}, rejectType={}", bizId, event.getRejectType());
            contractService.onRejected(bizId);
        } else if (TYPE_OUTPUT.equals(bizType)) {
            log.info("收到劳务产值审批驳回/撤回事件: id={}, rejectType={}", bizId, event.getRejectType());
            outputReportService.onRejected(bizId);
        } else if (TYPE_SETTLEMENT.equals(bizType)) {
            log.info("收到劳务结算审批驳回/撤回事件: id={}, rejectType={}", bizId, event.getRejectType());
            settlementService.onRejected(bizId);
        } else if (TYPE_PAYROLL.equals(bizType)) {
            log.info("收到劳务工资单审批驳回/撤回事件: id={}, rejectType={}", bizId, event.getRejectType());
            payrollService.onRejected(bizId);
        } else if (TYPE_REWARD_PUNISH.equals(bizType)) {
            log.info("收到劳务奖惩审批驳回/撤回事件: id={}, rejectType={}", bizId, event.getRejectType());
            rewardPunishService.onRejected(bizId);
        }
    }
}
