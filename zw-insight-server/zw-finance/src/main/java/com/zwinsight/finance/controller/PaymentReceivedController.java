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
    public R<PageResult<BizPaymentReceived>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(paymentReceivedService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizPaymentReceived paymentReceived) {
        paymentReceivedService.save(paymentReceived);
        return R.ok();
    }
}
