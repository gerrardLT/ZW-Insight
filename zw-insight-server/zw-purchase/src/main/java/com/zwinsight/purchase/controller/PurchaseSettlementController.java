package com.zwinsight.purchase.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.purchase.domain.BizPurchaseSettlement;
import com.zwinsight.purchase.readmodel.MaterialInboundView;
import com.zwinsight.purchase.service.PurchaseSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购结算接口
 */
@RestController
@RequestMapping("/api/v1/purchase/settlement")
@RequiredArgsConstructor
public class PurchaseSettlementController {

    private final PurchaseSettlementService purchaseSettlementService;

    @GetMapping("/page")
    public R<PageResult<BizPurchaseSettlement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String status) {
        return R.ok(purchaseSettlementService.page(page, size, projectId, contractId, status));
    }

    @GetMapping("/{id}")
    public R<BizPurchaseSettlement> getById(@PathVariable Long id) {
        return R.ok(purchaseSettlementService.getById(id));
    }

    /**
     * 查询指定合同下可结算的入库单（已审批且未结算）
     */
    @GetMapping("/available-inbounds")
    public R<List<MaterialInboundView>> availableInbounds(@RequestParam Long contractId) {
        return R.ok(purchaseSettlementService.availableInbounds(contractId));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizPurchaseSettlement settlement) {
        purchaseSettlementService.create(settlement);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizPurchaseSettlement settlement) {
        settlement.setId(id);
        purchaseSettlementService.update(settlement);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        purchaseSettlementService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        purchaseSettlementService.submit(id);
        return R.ok();
    }
}
