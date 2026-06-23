package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.service.InvoiceApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 开票申请接口
 */
@RestController
@RequestMapping("/api/v1/finance/invoice-apply")
@RequiredArgsConstructor
public class InvoiceApplyController {

    private final InvoiceApplyService invoiceApplyService;

    @GetMapping
    public R<PageResult<BizInvoiceApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(invoiceApplyService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizInvoiceApply invoiceApply) {
        invoiceApplyService.save(invoiceApply);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        invoiceApplyService.submit(id);
        return R.ok();
    }
}
