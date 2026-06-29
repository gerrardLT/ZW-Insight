package com.zwinsight.system.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.SysDict;
import com.zwinsight.system.domain.SysDictItem;
import com.zwinsight.system.service.SysDictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据字典接口
 */
@RestController
@RequestMapping("/api/v1/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService dictService;

    @GetMapping
    public R<PageResult<SysDict>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String dictName) {
        return R.ok(dictService.page(page, size, dictName));
    }

    @GetMapping("/{id}")
    public R<SysDict> getById(@PathVariable Long id) {
        return R.ok(dictService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody SysDict dict) {
        dictService.save(dict);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysDict dict) {
        dict.setId(id);
        dictService.update(dict);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        dictService.delete(id);
        return R.ok();
    }

    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestBody List<Long> ids) {
        dictService.batchDelete(ids);
        return R.ok();
    }

    @GetMapping("/items/{dictCode}")
    public R<List<SysDictItem>> getDictItemsByCode(@PathVariable String dictCode) {
        return R.ok(dictService.getDictItemsByCode(dictCode));
    }
}
