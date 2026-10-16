package com.zwinsight.finance.controller;

import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.annotation.FinanceLockCheck;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizInvoiceReceived;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.service.InvoiceReceivedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 收票登记接口
 */
@RestController
@RequestMapping("/api/v1/finance/invoice-received")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
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

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        invoiceReceivedService.delete(id);
        return R.ok();
    }

    /**
     * 查验状态流转（V2026_110：人工核对税务平台后如实标记，不冒充税局直连）。
     */
    @PostMapping("/{id}/verify")
    public R<Void> verify(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        invoiceReceivedService.verify(id, body.get("verifyStatus"));
        return R.ok();
    }

    /** 入账状态流转（纯登记，不动金额与合同累计） */
    @PostMapping("/{id}/bookkeep")
    public R<Void> markBookkeep(@PathVariable Long id, @RequestBody java.util.Map<String, Boolean> body) {
        Boolean booked = body.get("booked");
        if (booked == null) {
            return R.fail(400, "booked 不能为空");
        }
        invoiceReceivedService.markBookkeep(id, booked);
        return R.ok();
    }
}
