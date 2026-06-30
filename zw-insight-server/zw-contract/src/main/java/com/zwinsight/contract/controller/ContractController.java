package com.zwinsight.contract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizContractDetail;
import com.zwinsight.contract.service.ConstructionContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 施工合同接口
 */
@RestController
@RequestMapping("/api/v1/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ConstructionContractService contractService;

    @GetMapping
    public R<PageResult<BizConstructionContract>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return R.ok(contractService.page(page, size, projectId, status));
    }

    @GetMapping("/page")
    public R<PageResult<BizConstructionContract>> pageAlias(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return page(page, size, projectId, status);
    }

    @GetMapping("/{id}")
    public R<BizConstructionContract> getById(@PathVariable Long id) {
        return R.ok(contractService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizConstructionContract contract) {
        contractService.save(contract);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizConstructionContract contract) {
        contract.setId(id);
        contractService.update(contract);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        contractService.submit(id);
        return R.ok();
    }

    @PutMapping("/{id}/submit")
    public R<Void> submitByPut(@PathVariable Long id) {
        return submit(id);
    }

    @GetMapping("/{id}/details")
    public R<List<BizContractDetail>> getDetails(@PathVariable Long id) {
        return R.ok(contractService.getDetails(id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        contractService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/details")
    public R<Void> saveDetails(@PathVariable Long id, @RequestBody List<BizContractDetail> details) {
        contractService.saveDetails(id, details);
        return R.ok();
    }
}
