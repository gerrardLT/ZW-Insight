package com.zwinsight.subcontract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.service.SubcontractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 分包合同接口
 */
@RestController
@RequestMapping("/api/v1/subcontract/contract")
@RequiredArgsConstructor
public class SubcontractController {

    private final SubcontractService subcontractService;

    @GetMapping("/page")
    public R<PageResult<BizSubcontract>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(subcontractService.page(page, size, projectId));
    }

    @GetMapping("/{id}")
    public R<BizSubcontract> getById(@PathVariable Long id) {
        return R.ok(subcontractService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizSubcontract contract) {
        subcontractService.save(contract);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizSubcontract contract) {
        contract.setId(id);
        subcontractService.update(contract);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        subcontractService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        subcontractService.submit(id);
        return R.ok();
    }
}
