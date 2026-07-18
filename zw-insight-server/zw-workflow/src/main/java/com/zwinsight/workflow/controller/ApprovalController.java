package com.zwinsight.workflow.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.workflow.dto.*;
import com.zwinsight.workflow.service.ApprovalService;
import com.zwinsight.workflow.service.UrgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 审批流程接口
 */
@RestController
@RequestMapping("/api/v1/workflow/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;
    private final UrgeService urgeService;

    /**
     * 发起流程
     */
    @PostMapping("/start")
    public R<String> start(@RequestBody ProcessStartRequest request) {
        String processInstanceId = approvalService.startProcess(
                request.getBusinessType(),
                request.getBusinessId(),
                request.getProcessKey(),
                request.getVariables()
        );
        return R.ok(processInstanceId);
    }

    /**
     * 办理（通过）
     */
    @PostMapping("/complete")
    public R<Void> complete(@RequestBody TaskCompleteRequest request) {
        approvalService.complete(request.getTaskId(), request.getComment(), request.getVariables());
        return R.ok();
    }

    /**
     * 退回至上一节点
     */
    @PostMapping("/reject-previous")
    public R<Void> rejectToPrevious(@RequestBody TaskRejectRequest request) {
        approvalService.rejectToPrevious(request.getTaskId(), request.getComment());
        return R.ok();
    }

    /**
     * 退回至发起人
     */
    @PostMapping("/reject-start")
    public R<Void> rejectToStart(@RequestBody TaskRejectRequest request) {
        approvalService.rejectToStart(request.getTaskId(), request.getComment());
        return R.ok();
    }

    /**
     * 终止流程
     */
    @PostMapping("/terminate")
    public R<Void> terminate(@RequestBody TaskRejectRequest request) {
        approvalService.terminate(request.getTaskId(), request.getComment());
        return R.ok();
    }

    /**
     * 转办
     */
    @PostMapping("/transfer")
    public R<Void> transfer(@RequestBody TaskTransferRequest request) {
        approvalService.transfer(request.getTaskId(), request.getTargetUserId(), request.getComment());
        return R.ok();
    }

    /**
     * 委托
     */
    @PostMapping("/delegate")
    public R<Void> delegate(@RequestBody TaskTransferRequest request) {
        approvalService.delegate(request.getTaskId(), request.getTargetUserId(), request.getComment());
        return R.ok();
    }

    /**
     * 我的待办（分页）
     */
    @GetMapping("/todo")
    public R<PageResult<Map<String, Object>>> getTodoTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(approvalService.getMyTodoTasks(userId, page, size));
    }

    /**
     * 我的已办（分页）
     */
    @GetMapping("/done")
    public R<PageResult<Map<String, Object>>> getDoneTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(approvalService.getMyDoneTasks(userId, page, size));
    }

    /**
     * 我发起的流程（分页）
     */
    @GetMapping("/my-initiated")
    public R<PageResult<Map<String, Object>>> getMyInitiated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(approvalService.getMyInitiatedProcesses(userId, page, size));
    }

    /**
     * 批量通过
     */
    @PostMapping("/batch-approve")
    public R<Void> batchApprove(@RequestBody BatchApproveRequest request) {
        approvalService.batchApprove(request.getTaskIds(), request.getComment());
        return R.ok();
    }

    /**
     * 手动催办 - 发起人催办当前节点处理人
     */
    @PostMapping("/urge/{taskId}")
    public R<Void> urgeTask(@PathVariable String taskId) {
        Long userId = SecurityContextHolder.getUserId();
        urgeService.manualUrge(taskId, String.valueOf(userId));
        return R.ok();
    }

    /**
     * 查询指定任务的催办次数
     */
    @GetMapping("/urge/count/{taskId}")
    public R<Integer> getUrgeCount(@PathVariable String taskId) {
        return R.ok(urgeService.getUrgeCount(taskId));
    }
}
