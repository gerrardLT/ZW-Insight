package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.annotation.FinanceLockCheck;
import com.zwinsight.finance.domain.BizProjectReimbursement;
import com.zwinsight.finance.domain.BizReimbursementDetail;
import com.zwinsight.finance.service.ProjectReimbursementService;
import com.zwinsight.finance.service.ReimbursementDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目报销接口
 */
@RestController
@RequestMapping("/api/v1/finance/project-reimbursement")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class ProjectReimbursementController {

    private final ProjectReimbursementService reimbursementService;
    private final ReimbursementDetailService reimbursementDetailService;

    @GetMapping
    public R<PageResult<BizProjectReimbursement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(reimbursementService.page(page, size, projectId));
    }

    @PostMapping
    @FinanceLockCheck(dateField = "reimbursementDate", operation = "新增")
    public R<Long> save(@RequestBody BizProjectReimbursement reimbursement) {
        return R.ok(reimbursementService.save(reimbursement));
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        reimbursementService.submit(id);
        return R.ok();
    }

    /**
     * 查询报销单费用科目明细（含招待费专项信息；V2026_60）
     */
    @GetMapping("/{id}/details")
    public R<List<BizReimbursementDetail>> details(@PathVariable Long id) {
        return R.ok(reimbursementDetailService.listDetails(
                BizReimbursementDetail.SOURCE_PROJECT, id));
    }
}
