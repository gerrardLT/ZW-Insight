package com.zwinsight.basedata.controller;

import com.zwinsight.basedata.domain.BdOwner;
import com.zwinsight.basedata.service.OwnerService;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 甲方单位接口
 */
@RestController
@RequestMapping("/api/v1/basedata/owner")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    @GetMapping
    public R<PageResult<BdOwner>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String ownerName,
            @RequestParam(required = false) Integer status) {
        return R.ok(ownerService.page(page, size, ownerName, status));
    }

    @GetMapping("/{id}")
    public R<BdOwner> getById(@PathVariable Long id) {
        return R.ok(ownerService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BdOwner owner) {
        ownerService.save(owner);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BdOwner owner) {
        owner.setId(id);
        ownerService.update(owner);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ownerService.delete(id);
        return R.ok();
    }
}
