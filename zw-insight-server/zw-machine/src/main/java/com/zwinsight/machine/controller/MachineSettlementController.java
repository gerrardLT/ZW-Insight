package com.zwinsight.machine.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.machine.domain.BizMachineSettlement;
import com.zwinsight.machine.service.MachineSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 机械结算接口
 */
@RestController
@RequestMapping("/api/v1/machine/settlement")
@RequiredArgsConstructor
public class MachineSettlementController {

    private final MachineSettlementService settlementService;

    @GetMapping
    public R<PageResult<BizMachineSettlement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(settlementService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizMachineSettlement settlement) {
        settlementService.save(settlement);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        settlementService.submit(id);
        return R.ok();
    }
}
