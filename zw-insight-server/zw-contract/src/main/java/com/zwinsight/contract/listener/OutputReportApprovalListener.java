package com.zwinsight.contract.listener;

import com.zwinsight.contract.service.OutputReportService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 产值上报审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "OUTPUT_REPORT" 时分发到 {@link OutputReportService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 回写合同/项目累计产值 + BOQ 已完成工程量
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 状态置 REJECTED（数据未生效，无需回滚）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutputReportApprovalListener {

    private static final String BUSINESS_TYPE = "OUTPUT_REPORT";

    private final OutputReportService outputReportService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long reportId = event.getBusinessId();
        log.info("收到产值上报审批回调, reportId={}, result={}", reportId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            outputReportService.onApproved(reportId);
        }
    }

    /**
     * 审批驳回/撤回回调
     */
    @EventListener
    public void onApprovalReject(ApprovalRejectEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBizType())) {
            return;
        }
        Long reportId = event.getBizId();
        log.info("收到产值上报审批驳回回调, reportId={}, rejectType={}", reportId, event.getRejectType());
        outputReportService.onRejected(reportId);
    }
}
