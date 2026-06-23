package com.zwinsight.contract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.service.OtherContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 其他合同接口
 */
@RestController
@RequestMapping("/api/v1/contract/other")
@RequiredArgsConstructor
public class OtherContractController {

    private final OtherContractService otherContractService;

    @GetMapping
    public R<PageResult<BizOtherContract>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String contractCategory) {
        return R.ok(otherContractService.page(page, size, projectId, contractCategory));
    }

    @GetMapping("/{id}")
    public R<BizOtherContract> getById(@PathVariable Long id) {
        return R.ok(otherContractService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizOtherContract contract) {
        otherContractService.save(contract);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizOtherContract contract) {
        contract.setId(id);
        otherContractService.update(contract);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        otherContractService.delete(id);
        return R.ok();
    }
}
