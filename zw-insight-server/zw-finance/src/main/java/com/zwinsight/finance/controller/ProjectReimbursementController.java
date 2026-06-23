package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.BizProjectReimbursement;
import com.zwinsight.finance.service.ProjectReimbursementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 项目报销接口
 */
@RestController
@RequestMapping("/api/v1/finance/project-reimbursement")
@RequiredArgsConstructor
public class ProjectReimbursementController {

    private final ProjectReimbursementService reimbursementService;

    @GetMapping
    public R<PageResult<BizProjectReimbursement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(reimbursementService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizProjectReimbursement reimbursement) {
        reimbursementService.save(reimbursement);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        reimbursementService.submit(id);
        return R.ok();
    }
}
