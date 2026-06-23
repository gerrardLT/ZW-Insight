package com.zwinsight.purchase.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.purchase.domain.BizPurchaseSettlement;
import com.zwinsight.purchase.service.PurchaseSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 采购结算接口
 */
@RestController
@RequestMapping("/api/v1/purchase/settlement")
@RequiredArgsConstructor
public class PurchaseSettlementController {

    private final PurchaseSettlementService purchaseSettlementService;

    @GetMapping
    public R<PageResult<BizPurchaseSettlement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(purchaseSettlementService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizPurchaseSettlement settlement) {
        purchaseSettlementService.save(settlement);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        purchaseSettlementService.submit(id);
        return R.ok();
    }
}
