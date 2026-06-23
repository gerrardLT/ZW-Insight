package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.BizReserveFundApply;
import com.zwinsight.finance.domain.BizReserveFundReturn;
import com.zwinsight.finance.service.ReserveFundApplyService;
import com.zwinsight.finance.service.ReserveFundReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 备用金管理接口（含申请和归还）
 */
@RestController
@RequestMapping("/api/v1/finance/reserve-fund")
@RequiredArgsConstructor
public class ReserveFundController {

    private final ReserveFundApplyService applyService;
    private final ReserveFundReturnService returnService;

    // ===== 备用金申请 =====

    @GetMapping("/apply")
    public R<PageResult<BizReserveFundApply>> applyPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(applyService.page(page, size, projectId));
    }

    @PostMapping("/apply")
    public R<Void> saveApply(@RequestBody BizReserveFundApply apply) {
        applyService.save(apply);
        return R.ok();
    }

    @PostMapping("/apply/{id}/submit")
    public R<Void> submitApply(@PathVariable Long id) {
        applyService.submit(id);
        return R.ok();
    }

    // ===== 备用金归还 =====

    @PostMapping("/return")
    public R<Void> saveReturn(@RequestBody BizReserveFundReturn fundReturn) {
        returnService.save(fundReturn);
        return R.ok();
    }
}
