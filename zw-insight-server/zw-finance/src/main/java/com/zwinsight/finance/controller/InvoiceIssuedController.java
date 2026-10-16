package com.zwinsight.finance.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizInvoiceIssued;
import com.zwinsight.finance.service.InvoiceIssuedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 销项发票台账接口（V2026_110，C3）。
 * <p>记录真实票据事实：登记（含红冲挂原票）、查验、入账；不改合同累计开票口径。
 * 权限复用 finance:invoiceapply:*（发票族既有码；另建新码会落「无菜单承载」缺口）。</p>
 */
@RestController
@RequestMapping("/api/v1/finance/invoice-issued")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class InvoiceIssuedController {

    private final InvoiceIssuedService invoiceIssuedService;

    @GetMapping
    public R<PageResult<BizInvoiceIssued>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Long applyId) {
        return R.ok(invoiceIssuedService.page(page, size, projectId, contractId, direction, applyId));
    }

    @PostMapping
    @RequiresPermission("finance:invoiceapply:add")
    public R<Void> save(@RequestBody BizInvoiceIssued invoice) {
        invoiceIssuedService.save(invoice);
        return R.ok();
    }

    @PostMapping("/{id}/verify")
    @RequiresPermission("finance:invoiceapply:edit")
    public R<Void> verify(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long verifiedBy = SecurityContextHolder.getUserId();
        invoiceIssuedService.verify(id, body.get("verifyStatus"), verifiedBy);
        return R.ok();
    }

    @PostMapping("/{id}/bookkeep")
    @RequiresPermission("finance:invoice:edit")
    public R<Void> markBookkeep(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean booked = body.get("booked");
        if (booked == null) {
            return R.fail(400, "booked 不能为空");
        }
        invoiceIssuedService.markBookkeep(id, booked);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("finance:invoiceapply:delete")
    public R<Void> delete(@PathVariable Long id) {
        invoiceIssuedService.delete(id);
        return R.ok();
    }
}
