package com.zwinsight.purchase.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseContractDetail;
import com.zwinsight.purchase.service.PurchaseContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购合同接口
 */
@RestController
@RequestMapping("/api/v1/purchase/contract")
@RequiredArgsConstructor
public class PurchaseContractController {

    private final PurchaseContractService purchaseContractService;

    @GetMapping("/page")
    public R<PageResult<BizPurchaseContract>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return R.ok(purchaseContractService.page(page, size, projectId, status));
    }

    @GetMapping("/{id}")
    public R<BizPurchaseContract> getById(@PathVariable Long id) {
        return R.ok(purchaseContractService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizPurchaseContract contract) {
        purchaseContractService.save(contract);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizPurchaseContract contract) {
        contract.setId(id);
        purchaseContractService.update(contract);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        purchaseContractService.submit(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        purchaseContractService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/details")
    public R<List<BizPurchaseContractDetail>> getDetails(@PathVariable Long id) {
        return R.ok(purchaseContractService.getDetails(id));
    }
}
