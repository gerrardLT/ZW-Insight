package com.zwinsight.file.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.file.domain.FileStorage;
import com.zwinsight.file.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 存储配置接口
 */
@RestController
@RequestMapping("/api/v1/file/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @GetMapping
    public R<PageResult<FileStorage>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(storageService.page(page, size));
    }

    @GetMapping("/{id}")
    public R<FileStorage> getById(@PathVariable Long id) {
        return R.ok(storageService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody FileStorage storage) {
        storageService.save(storage);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody FileStorage storage) {
        storage.setId(id);
        storageService.update(storage);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        storageService.delete(id);
        return R.ok();
    }
}
