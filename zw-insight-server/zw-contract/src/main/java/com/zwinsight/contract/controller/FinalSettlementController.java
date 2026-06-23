package com.zwinsight.contract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.contract.domain.BizFinalSettlement;
import com.zwinsight.contract.service.FinalSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 竣工结算接口
 */
@RestController
@RequestMapping("/api/v1/contract/settlement")
@RequiredArgsConstructor
public class FinalSettlementController {

    private final FinalSettlementService finalSettlementService;

    @GetMapping
    public R<PageResult<BizFinalSettlement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(finalSettlementService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizFinalSettlement settlement) {
        finalSettlementService.save(settlement);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        finalSettlementService.submit(id);
        return R.ok();
    }
}
