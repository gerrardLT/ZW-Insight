package com.zwinsight.contract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.contract.domain.BizQuantityList;
import com.zwinsight.contract.service.QuantityListService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 工程量清单接口
 */
@RestController
@RequestMapping("/api/v1/contract/quantity")
@RequiredArgsConstructor
public class QuantityListController {

    private final QuantityListService quantityListService;

    @GetMapping
    public R<PageResult<BizQuantityList>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(quantityListService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizQuantityList quantityList) {
        quantityListService.save(quantityList);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizQuantityList quantityList) {
        quantityList.setId(id);
        quantityListService.update(quantityList);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        quantityListService.delete(id);
        return R.ok();
    }

    @PostMapping("/import")
    public R<Integer> batchImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long projectId,
            @RequestParam Long contractId) {
        int count = quantityListService.batchImport(file, projectId, contractId);
        return R.ok("导入成功，共" + count + "条", count);
    }
}
