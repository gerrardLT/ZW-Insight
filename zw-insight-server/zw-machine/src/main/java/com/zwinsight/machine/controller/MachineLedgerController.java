package com.zwinsight.machine.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.service.MachineLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 机械台账接口
 */
@RestController
@RequestMapping("/api/v1/machine/ledger")
@RequiredArgsConstructor
public class MachineLedgerController {

    private final MachineLedgerService ledgerService;

    @GetMapping
    public R<PageResult<BizMachineLedger>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String machineName,
            @RequestParam(required = false) String machineType) {
        return R.ok(ledgerService.page(page, size, machineName, machineType));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizMachineLedger ledger) {
        ledgerService.save(ledger);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMachineLedger ledger) {
        ledger.setId(id);
        ledgerService.update(ledger);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ledgerService.delete(id);
        return R.ok();
    }
}
