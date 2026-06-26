package com.zwinsight.labor.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.labor.domain.BizLaborSettlement;
import com.zwinsight.labor.service.LaborSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 劳务结算接口
 */
@RestController
@RequestMapping("/api/v1/labor/settlement")
@RequiredArgsConstructor
public class LaborSettlementController {

    private final LaborSettlementService settlementService;

    @GetMapping("/page")
    public R<PageResult<BizLaborSettlement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(settlementService.page(page, size, projectId, contractId));
    }

    @GetMapping("/{id}")
    public R<BizLaborSettlement> getById(@PathVariable Long id) {
        return R.ok(settlementService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizLaborSettlement settlement) {
        settlementService.save(settlement);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizLaborSettlement settlement) {
        settlement.setId(id);
        settlementService.update(settlement);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        settlementService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        settlementService.submit(id);
        return R.ok();
    }
}
