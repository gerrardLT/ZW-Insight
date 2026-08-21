package com.zwinsight.finance.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.annotation.FinanceLockCheck;
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
@RequiresPermission("finance:view")
public class PaymentApplyController {

    private final PaymentApplyService paymentApplyService;

    @GetMapping("/page")
    public R<PageResult<BizPaymentApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String status) {
        return R.ok(paymentApplyService.page(page, size, projectId, contractId, status));
    }

    @GetMapping("/{id}")
    public R<BizPaymentApply> getById(@PathVariable Long id) {
        return R.ok(paymentApplyService.getById(id));
    }

    @PostMapping
    @FinanceLockCheck(dateField = "paymentDate", operation = "新增")
    @OperLog(module = "付款管理", operType = "INSERT", description = "新增付款申请")
    public R<Void> save(@RequestBody BizPaymentApply paymentApply) {
        paymentApplyService.save(paymentApply);
        return R.ok();
    }

    @PutMapping("/{id}")
    @FinanceLockCheck(dateField = "paymentDate", operation = "编辑")
    public R<Void> update(@PathVariable Long id, @RequestBody BizPaymentApply paymentApply) {
        paymentApply.setId(id);
        paymentApplyService.update(paymentApply);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        paymentApplyService.delete(id);
        return R.ok();
    }

    @RequestMapping(value = "/{id}/submit", method = {RequestMethod.POST, RequestMethod.PUT})
    @RequiresPermission("finance:payment:submit")
    @OperLog(module = "付款管理", operType = "UPDATE", description = "提交付款申请审批")
    public R<Void> submit(@PathVariable Long id) {
        paymentApplyService.submit(id);
        return R.ok();
    }
}
