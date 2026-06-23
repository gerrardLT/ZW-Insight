package com.zwinsight.basedata.controller;

import com.zwinsight.basedata.domain.BdSupplier;
import com.zwinsight.basedata.service.SupplierService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 供应商接口
 */
@RestController
@RequestMapping("/api/v1/basedata/supplier")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public R<PageResult<BdSupplier>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String supplierType,
            @RequestParam(required = false) Integer status) {
        return R.ok(supplierService.page(page, size, supplierName, supplierType, status));
    }

    @GetMapping("/{id}")
    public R<BdSupplier> getById(@PathVariable Long id) {
        return R.ok(supplierService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BdSupplier supplier) {
        supplierService.save(supplier);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BdSupplier supplier) {
        supplier.setId(id);
        supplierService.update(supplier);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return R.ok();
    }

    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestBody List<Long> ids) {
        supplierService.batchDelete(ids);
        return R.ok();
    }

    @PostMapping("/import")
    public R<Integer> importSuppliers(@RequestParam("file") MultipartFile file) {
        int count = supplierService.importSuppliers(file);
        return R.ok("成功导入" + count + "条数据", count);
    }
}
