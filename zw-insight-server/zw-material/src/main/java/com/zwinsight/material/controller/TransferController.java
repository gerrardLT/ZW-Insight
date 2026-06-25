package com.zwinsight.material.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.material.domain.BizMaterialTransfer;
import com.zwinsight.material.domain.BizMaterialTransferDetail;
import com.zwinsight.material.service.MaterialTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 材料调拨接口
 */
@RestController
@RequestMapping("/api/v1/material/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final MaterialTransferService transferService;

    @GetMapping
    @GetMapping("/page")
    public R<PageResult<BizMaterialTransfer>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long fromProjectId,
            @RequestParam(required = false) Long toProjectId) {
        return R.ok(transferService.page(page, size, fromProjectId, toProjectId));
    }

    @GetMapping("/{id}")
    public R<BizMaterialTransfer> getById(@PathVariable Long id) {
        return R.ok(transferService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody Map<String, Object> body) {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transferService.save(transfer, List.of());
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BizMaterialTransfer transfer = new BizMaterialTransfer();
        transfer.setId(id);
        transferService.update(transfer);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        transferService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        transferService.submit(id);
        return R.ok();
    }
}
