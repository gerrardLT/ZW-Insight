package com.zwinsight.finance.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.annotation.FinanceLockCheck;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.dto.InvoiceApplyCreateRequest;
import com.zwinsight.finance.service.InvoiceApplyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 开票申请接口
 */
@RestController
@RequestMapping("/api/v1/finance/invoice-apply")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class InvoiceApplyController {

    private final InvoiceApplyService invoiceApplyService;

    @GetMapping("/page")
    public R<PageResult<BizInvoiceApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String status) {
        return R.ok(invoiceApplyService.page(page, size, projectId, contractId, status));
    }

    @GetMapping("/{id}")
    public R<BizInvoiceApply> getById(@PathVariable Long id) {
        return R.ok(invoiceApplyService.getById(id));
    }

    @PostMapping
    @FinanceLockCheck(dateField = "applyDate", operation = "新增")
    @RequiresPermission("finance:invoiceapply:add")
    @OperLog(module = "开票管理", operType = "INSERT", description = "新增开票申请")
    public R<Void> save(@Valid @RequestBody InvoiceApplyCreateRequest request) {
        invoiceApplyService.saveFromRequest(request);
        return R.ok();
    }

    @PutMapping("/{id}")
    @FinanceLockCheck(dateField = "applyDate", operation = "编辑")
    @RequiresPermission("finance:invoiceapply:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody InvoiceApplyCreateRequest request) {
        invoiceApplyService.updateFromRequest(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("finance:invoiceapply:delete")
    public R<Void> delete(@PathVariable Long id) {
        invoiceApplyService.delete(id);
        return R.ok();
    }

    @RequestMapping(value = "/{id}/submit", method = {RequestMethod.POST, RequestMethod.PUT})
    @RequiresPermission("finance:invoiceapply:submit")
    @OperLog(module = "开票管理", operType = "UPDATE", description = "提交开票申请审批")
    public R<Void> submit(@PathVariable Long id) {
        invoiceApplyService.submit(id);
        return R.ok();
    }
}
