package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.service.PaymentReceivedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 收款登记接口
 */
@RestController
@RequestMapping("/api/v1/finance/payment-received")
@RequiredArgsConstructor
public class PaymentReceivedController {

    private final PaymentReceivedService paymentReceivedService;

    @GetMapping
    @GetMapping("/page")
    public R<PageResult<BizPaymentReceived>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(paymentReceivedService.page(page, size, projectId));
    }

    @GetMapping("/{id}")
    public R<BizPaymentReceived> getById(@PathVariable Long id) {
        return R.ok(paymentReceivedService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizPaymentReceived paymentReceived) {
        paymentReceivedService.save(paymentReceived);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizPaymentReceived paymentReceived) {
        paymentReceived.setId(id);
        paymentReceivedService.update(paymentReceived);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        paymentReceivedService.delete(id);
        return R.ok();
    }
}
