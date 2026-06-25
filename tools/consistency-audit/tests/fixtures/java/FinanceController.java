package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.domain.BizReimbursement;
import com.zwinsight.finance.service.FinanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 财务管理接口
 */
@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;

    // ======================== 开票申请 ========================

    @GetMapping("/invoice-apply/page")
    public R<PageResult<BizInvoiceApply>> getInvoiceApplyPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(financeService.getInvoiceApplyPage(page, size, projectId));
    }

    @GetMapping("/invoice-apply/{id}")
    public R<BizInvoiceApply> getInvoiceApplyDetail(@PathVariable Long id) {
        return R.ok(financeService.getInvoiceApplyById(id));
    }

    @PostMapping("/invoice-apply")
    public R<Void> createInvoiceApply(@Valid @RequestBody BizInvoiceApply data) {
        financeService.createInvoiceApply(data);
        return R.ok();
    }

    @PutMapping("/invoice-apply")
    public R<Void> updateInvoiceApply(@Valid @RequestBody BizInvoiceApply data) {
        financeService.updateInvoiceApply(data);
        return R.ok();
    }

    @DeleteMapping("/invoice-apply/{id}")
    public R<Void> deleteInvoiceApply(@PathVariable Long id) {
        financeService.deleteInvoiceApply(id);
        return R.ok();
    }

    @PutMapping("/invoice-apply/{id}/submit")
    public R<Void> submitInvoiceApply(@PathVariable Long id) {
        financeService.submitInvoiceApply(id);
        return R.ok();
    }

    // ======================== 回款登记 ========================

    @GetMapping("/payment-received/page")
    public R<PageResult<BizPaymentReceived>> getPaymentReceivedPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(financeService.getPaymentReceivedPage(page, size, projectId));
    }

    @GetMapping("/payment-received/{id}")
    public R<BizPaymentReceived> getPaymentReceivedDetail(@PathVariable Long id) {
        return R.ok(financeService.getPaymentReceivedById(id));
    }

    @PostMapping("/payment-received")
    public R<Void> createPaymentReceived(@Valid @RequestBody BizPaymentReceived data) {
        financeService.createPaymentReceived(data);
        return R.ok();
    }

    @PutMapping("/payment-received")
    public R<Void> updatePaymentReceived(@Valid @RequestBody BizPaymentReceived data) {
        financeService.updatePaymentReceived(data);
        return R.ok();
    }

    @DeleteMapping("/payment-received/{id}")
    public R<Void> deletePaymentReceived(@PathVariable Long id) {
        financeService.deletePaymentReceived(id);
        return R.ok();
    }

    // ======================== 付款申请 ========================

    @GetMapping("/payment-apply/page")
    public R<PageResult<BizPaymentApply>> getPaymentApplyPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(financeService.getPaymentApplyPage(page, size));
    }

    @PostMapping("/payment-apply")
    public R<Void> createPaymentApply(@Valid @RequestBody BizPaymentApply data) {
        financeService.createPaymentApply(data);
        return R.ok();
    }

    @PutMapping("/payment-apply/{id}/submit")
    public R<Void> submitPaymentApply(@PathVariable Long id) {
        financeService.submitPaymentApply(id);
        return R.ok();
    }

    // ======================== 项目报销 ========================

    @GetMapping(value = "/project-reimbursement/page")
    public R<PageResult<BizReimbursement>> getReimbursementPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(financeService.getReimbursementPage(page, size));
    }

    @PostMapping(value = "/project-reimbursement")
    public R<Void> createReimbursement(@Valid @RequestBody BizReimbursement data) {
        financeService.createReimbursement(data);
        return R.ok();
    }
}
