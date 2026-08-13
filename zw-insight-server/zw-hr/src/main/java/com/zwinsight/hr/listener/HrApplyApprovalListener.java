package com.zwinsight.hr.listener;

import com.zwinsight.hr.service.EntryApplyService;
import com.zwinsight.hr.service.RegularApplyService;
import com.zwinsight.hr.service.ResignApplyService;
import com.zwinsight.hr.service.SealApplyService;
import com.zwinsight.hr.service.TransferApplyService;
import com.zwinsight.hr.service.VehicleApplyService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 人事申请审批回调监听器（入职/转正/调动/离职/用印/车辆六类）
 * <p>
 * P1 修复（2026-08-13，批次三取证枚举）：原实现六个申请 submit 均「提交即置 APPROVED」
 * 并立即执行业务副作用（建账号/停用账号/变更部门岗位/车辆置 IN_USE），审批形同虚设，
 * 驳回后副作用无法回收。本监听器补齐审批后生效闭环：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 各 Service.onApproved（副作用在此执行）
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 各 Service.onRejected（回退 DRAFT）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HrApplyApprovalListener {

    private static final String ENTRY = "ENTRY_APPLY";
    private static final String REGULAR = "REGULAR_APPLY";
    private static final String TRANSFER = "TRANSFER_APPLY";
    private static final String RESIGN = "RESIGN_APPLY";
    private static final String SEAL = "SEAL_APPLY";
    private static final String VEHICLE = "VEHICLE_APPLY";

    private final EntryApplyService entryApplyService;
    private final RegularApplyService regularApplyService;
    private final TransferApplyService transferApplyService;
    private final ResignApplyService resignApplyService;
    private final SealApplyService sealApplyService;
    private final VehicleApplyService vehicleApplyService;

    /**
     * 审批通过回调分发
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!"APPROVED".equals(event.getResult())) {
            return;
        }
        Long applyId = event.getBusinessId();
        switch (event.getBusinessType() == null ? "" : event.getBusinessType()) {
            case ENTRY -> entryApplyService.onApproved(applyId);
            case REGULAR -> regularApplyService.onApproved(applyId);
            case TRANSFER -> transferApplyService.onApproved(applyId);
            case RESIGN -> resignApplyService.onApproved(applyId);
            case SEAL -> sealApplyService.onApproved(applyId);
            case VEHICLE -> vehicleApplyService.onApproved(applyId);
            default -> {
                // 非人事业务类型，忽略
            }
        }
    }

    /**
     * 审批驳回/撤回回调分发
     */
    @EventListener
    public void onApprovalReject(ApprovalRejectEvent event) {
        Long applyId = event.getBizId();
        switch (event.getBizType() == null ? "" : event.getBizType()) {
            case ENTRY -> entryApplyService.onRejected(applyId);
            case REGULAR -> regularApplyService.onRejected(applyId);
            case TRANSFER -> transferApplyService.onRejected(applyId);
            case RESIGN -> resignApplyService.onRejected(applyId);
            case SEAL -> sealApplyService.onRejected(applyId);
            case VEHICLE -> vehicleApplyService.onRejected(applyId);
            default -> {
                // 非人事业务类型，忽略
            }
        }
    }
}
