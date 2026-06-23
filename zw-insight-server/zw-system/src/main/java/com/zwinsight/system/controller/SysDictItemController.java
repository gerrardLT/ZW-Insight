package com.zwinsight.system.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.SysDictItem;
import com.zwinsight.system.service.SysDictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典值管理接口
 */
@RestController
@RequestMapping("/v1/system/dict-item")
@RequiredArgsConstructor
public class SysDictItemController {

    private final SysDictItemService dictItemService;

    @GetMapping("/{dictId}")
    public R<List<SysDictItem>> list(@PathVariable Long dictId) {
        return R.ok(dictItemService.list(dictId));
    }

    @PostMapping
    public R<Void> save(@RequestBody SysDictItem item) {
        dictItemService.save(item);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysDictItem item) {
        item.setId(id);
        dictItemService.update(item);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        dictItemService.delete(id);
        return R.ok();
    }
}
