package com.zwinsight.machine.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.machine.domain.BizMachineContract;
import com.zwinsight.machine.service.MachineContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 机械合同接口
 */
@RestController
@RequestMapping("/api/v1/machine/contract")
@RequiredArgsConstructor
public class MachineContractController {

    private final MachineContractService contractService;

    @GetMapping("/page")
    public R<PageResult<BizMachineContract>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String contractName,
            @RequestParam(required = false) String supplierName) {
        return R.ok(contractService.page(page, size, projectId, contractName, supplierName));
    }

    @GetMapping("/{id}")
    public R<BizMachineContract> getById(@PathVariable Long id) {
        return R.ok(contractService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizMachineContract contract) {
        contractService.save(contract);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMachineContract contract) {
        contract.setId(id);
        contractService.update(contract);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        contractService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        contractService.submit(id);
        return R.ok();
    }
}
