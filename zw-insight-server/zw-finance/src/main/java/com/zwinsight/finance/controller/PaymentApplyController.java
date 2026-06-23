package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.service.PaymentApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 付款申请接口
 */
@RestController
@RequestMapping("/api/v1/finance/payment-apply")
@RequiredArgsConstructor
public class PaymentApplyController {

    private final PaymentApplyService paymentApplyService;

    @GetMapping
    public R<PageResult<BizPaymentApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(paymentApplyService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizPaymentApply paymentApply) {
        paymentApplyService.save(paymentApply);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        paymentApplyService.submit(id);
        return R.ok();
    }
}
