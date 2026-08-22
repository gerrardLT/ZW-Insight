package com.zwinsight.finance.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.annotation.FinanceLockCheck;
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
@RequiresPermission("finance:view")
public class PaymentReceivedController {

    private final PaymentReceivedService paymentReceivedService;

    @GetMapping("/page")
    public R<PageResult<BizPaymentReceived>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String claimStatus) {
        return R.ok(paymentReceivedService.page(page, size, projectId, claimStatus));
    }

    @GetMapping("/{id}")
    public R<BizPaymentReceived> getById(@PathVariable Long id) {
        return R.ok(paymentReceivedService.getById(id));
    }

    @PostMapping
    @FinanceLockCheck(dateField = "receiveDate", operation = "新增")
    public R<Void> save(@RequestBody BizPaymentReceived paymentReceived) {
        paymentReceivedService.save(paymentReceived);
        return R.ok();
    }

    @PutMapping("/{id}")
    @FinanceLockCheck(dateField = "receiveDate", operation = "编辑")
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

    /**
     * 认领回款（UNCLAIMED → CLAIMED）
     */
    @PostMapping("/{id}/claim")
    @RequiresPermission("finance:paymentreceived:claim")
    @OperLog(module = "收款登记", operType = "UPDATE", description = "认领回款")
    public R<Void> claim(@PathVariable Long id) {
        paymentReceivedService.claim(id);
        return R.ok();
    }

    /**
     * 核销回款（CLAIMED → WRITTEN_OFF）
     */
    @PostMapping("/{id}/write-off")
    @RequiresPermission("finance:paymentreceived:writeoff")
    @OperLog(module = "收款登记", operType = "UPDATE", description = "核销回款")
    public R<Void> writeOff(@PathVariable Long id) {
        paymentReceivedService.writeOff(id);
        return R.ok();
    }
}
