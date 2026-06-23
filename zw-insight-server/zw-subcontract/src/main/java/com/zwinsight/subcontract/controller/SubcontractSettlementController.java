package com.zwinsight.subcontract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.subcontract.domain.BizSubcontractSettlement;
import com.zwinsight.subcontract.service.SubcontractSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 分包结算接口
 */
@RestController
@RequestMapping("/api/v1/subcontract/settlement")
@RequiredArgsConstructor
public class SubcontractSettlementController {

    private final SubcontractSettlementService settlementService;

    @GetMapping
    public R<PageResult<BizSubcontractSettlement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(settlementService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizSubcontractSettlement settlement) {
        settlementService.save(settlement);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        settlementService.submit(id);
        return R.ok();
    }
}
