package com.zwinsight.finance.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.security.annotation.SecondaryConfirm;
import com.zwinsight.finance.annotation.FinanceLockCheck;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.dto.BatchOperationRequest;
import com.zwinsight.finance.service.PaymentApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String payStatus) {
        return R.ok(paymentApplyService.page(page, size, projectId, contractId, status, payStatus));
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

    /**
     * 批量操作（删除草稿 / 提交审批）。
     * <p>危险批量操作走 {@code SecondaryConfirm} 二次确认（449 → 前端弹密码框携带
     * X-Confirm-Password 重发）；整体单事务，任一单据状态非法则全部回滚。</p>
     */
    @PostMapping("/batch")
    @RequiresPermission("finance:payment:submit")
    @SecondaryConfirm(message = "批量操作付款申请为高风险操作，请输入登录密码确认")
    @OperLog(module = "付款管理", operType = "BATCH", description = "批量操作付款申请")
    public R<Integer> batch(@RequestBody BatchOperationRequest request) {
        return R.ok(paymentApplyService.batch(request));
    }

    /**
     * 手工标记已支付（V2026_56；无网银流水导入场景的备选路径，已勾稽流水的单据拒绝手工标记）
     */
    @PostMapping("/{id}/mark-paid")
    @RequiresPermission("finance:payment:submit")
    @OperLog(module = "付款管理", operType = "UPDATE", description = "手工标记付款申请已支付")
    public R<Void> markPaid(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate payDate,
            @RequestParam(required = false) Long payAccountId) {
        paymentApplyService.markPaid(id, payDate, payAccountId);
        return R.ok();
    }

    /**
     * 撤销手工支付标记（V2026_56；流水勾稽产生的支付态须走取消勾稽）
     */
    @PostMapping("/{id}/revoke-paid")
    @RequiresPermission("finance:payment:submit")
    @OperLog(module = "付款管理", operType = "UPDATE", description = "撤销付款申请手工支付标记")
    public R<Void> revokePaid(@PathVariable Long id) {
        paymentApplyService.revokePaid(id);
        return R.ok();
    }
}
