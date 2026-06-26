package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.annotation.FinanceLockCheck;
import com.zwinsight.finance.domain.BizInvoiceReceived;
import com.zwinsight.finance.service.InvoiceReceivedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 收票登记接口
 */
@RestController
@RequestMapping("/api/v1/finance/invoice-received")
@RequiredArgsConstructor
public class InvoiceReceivedController {

    private final InvoiceReceivedService invoiceReceivedService;

    @GetMapping
    public R<PageResult<BizInvoiceReceived>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(invoiceReceivedService.page(page, size, projectId));
    }

    @PostMapping
    @FinanceLockCheck(dateField = "invoiceDate", operation = "新增")
    public R<Void> save(@RequestBody BizInvoiceReceived invoiceReceived) {
        invoiceReceivedService.save(invoiceReceived);
        return R.ok();
    }
}
