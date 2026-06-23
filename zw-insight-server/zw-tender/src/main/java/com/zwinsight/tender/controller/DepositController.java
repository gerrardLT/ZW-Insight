package com.zwinsight.tender.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.tender.domain.BizDepositApply;
import com.zwinsight.tender.domain.BizDepositReturn;
import com.zwinsight.tender.service.DepositApplyService;
import com.zwinsight.tender.service.DepositReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 保证金管理接口（含申请和退还）
 */
@RestController
@RequestMapping("/api/v1/tender/deposit")
@RequiredArgsConstructor
public class DepositController {

    private final DepositApplyService applyService;
    private final DepositReturnService returnService;

    // ===== 保证金申请 =====

    @GetMapping("/apply")
    public R<PageResult<BizDepositApply>> applyPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(applyService.page(page, size, projectId));
    }

    @PostMapping("/apply")
    public R<Void> saveApply(@RequestBody BizDepositApply apply) {
        applyService.save(apply);
        return R.ok();
    }

    @PostMapping("/apply/{id}/submit")
    public R<Void> submitApply(@PathVariable Long id) {
        applyService.submit(id);
        return R.ok();
    }

    // ===== 保证金退还 =====

    @GetMapping("/return")
    public R<PageResult<BizDepositReturn>> returnPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long depositApplyId) {
        return R.ok(returnService.page(page, size, depositApplyId));
    }

    @PostMapping("/return")
    public R<Void> saveReturn(@RequestBody BizDepositReturn depositReturn) {
        returnService.save(depositReturn);
        return R.ok();
    }
}
