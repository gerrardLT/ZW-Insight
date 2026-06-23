package com.zwinsight.contract.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.contract.domain.BizBomItem;
import com.zwinsight.contract.service.BomItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 工程量清单接口
 */
@RestController
@RequestMapping("/api/v1/contract/bom")
@RequiredArgsConstructor
public class BomItemController {

    private final BomItemService bomItemService;

    @GetMapping("/{contractId}")
    public R<List<BizBomItem>> list(@PathVariable Long contractId) {
        return R.ok(bomItemService.list(contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizBomItem item) {
        bomItemService.save(item);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizBomItem item) {
        item.setId(id);
        bomItemService.update(item);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        bomItemService.delete(id);
        return R.ok();
    }

    @PostMapping("/import")
    public R<Void> batchImport(@RequestParam Long projectId,
                               @RequestParam Long contractId,
                               @RequestParam("file") MultipartFile file) {
        bomItemService.batchImport(projectId, contractId, file);
        return R.ok();
    }
}
