package com.zwinsight.workflow.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.workflow.dto.ConflictConfirmRequest;
import com.zwinsight.workflow.dto.RollbackLogQuery;
import com.zwinsight.workflow.dto.RollbackLogVO;
import com.zwinsight.workflow.service.rollback.ApprovalRollbackService;
import com.zwinsight.workflow.service.rollback.RollbackResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 审批数据回滚 REST API
 */
@RestController
@RequestMapping("/api/v1/workflow/rollback")
@RequiredArgsConstructor
public class ApprovalRollbackController {

    private final ApprovalRollbackService approvalRollbackService;

    /**
     * 回滚记录查询（分页）
     */
    @GetMapping("/logs")
    public R<PageResult<RollbackLogVO>> queryLogs(RollbackLogQuery query) {
        PageResult<RollbackLogVO> result = approvalRollbackService.queryRollbackLogs(query);
        return R.ok(result);
    }

    /**
     * 确认冲突处理
     */
    @PostMapping("/{id}/confirm")
    public R<Void> confirmConflict(@PathVariable Long id, @RequestBody ConflictConfirmRequest request) {
        approvalRollbackService.confirmConflict(id, request.getResolution());
        return R.ok();
    }

    /**
     * 手动触发回滚（用于补偿场景）
     */
    @PostMapping("/{workflowInstanceId}/execute")
    public R<RollbackResult> executeRollback(@PathVariable String workflowInstanceId) {
        RollbackResult result = approvalRollbackService.executeRollback(workflowInstanceId);
        return R.ok(result);
    }
}
