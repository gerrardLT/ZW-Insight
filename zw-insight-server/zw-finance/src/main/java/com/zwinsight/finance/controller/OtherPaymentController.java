package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.BizOtherPayment;
import com.zwinsight.finance.service.OtherPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 其他支付接口
 */
@RestController
@RequestMapping("/api/v1/finance/other-payment")
@RequiredArgsConstructor
public class OtherPaymentController {

    private final OtherPaymentService otherPaymentService;

    @GetMapping
    public R<PageResult<BizOtherPayment>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(otherPaymentService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizOtherPayment otherPayment) {
        otherPaymentService.save(otherPayment);
        return R.ok();
    }
}
