package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.annotation.FinanceLockCheck;
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

    @GetMapping("/page")
    public R<PageResult<BizInvoiceApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(invoiceApplyService.page(page, size, projectId, contractId));
    }

    @GetMapping("/{id}")
    public R<BizInvoiceApply> getById(@PathVariable Long id) {
        return R.ok(invoiceApplyService.getById(id));
    }

    @PostMapping
    @FinanceLockCheck(dateField = "applyDate", operation = "新增")
    public R<Void> save(@RequestBody BizInvoiceApply invoiceApply) {
        invoiceApplyService.save(invoiceApply);
        return R.ok();
    }

    @PutMapping("/{id}")
    @FinanceLockCheck(dateField = "applyDate", operation = "编辑")
    public R<Void> update(@PathVariable Long id, @RequestBody BizInvoiceApply invoiceApply) {
        invoiceApply.setId(id);
        invoiceApplyService.update(invoiceApply);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        invoiceApplyService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        invoiceApplyService.submit(id);
        return R.ok();
    }
}
