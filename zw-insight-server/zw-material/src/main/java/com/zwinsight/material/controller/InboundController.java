package com.zwinsight.material.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.material.domain.BizMaterialInbound;
import com.zwinsight.material.domain.BizMaterialInboundDetail;
import com.zwinsight.material.service.MaterialInboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 材料入库接口
 */
@RestController
@RequestMapping("/api/v1/material/inbound")
@RequiredArgsConstructor
public class InboundController {

    private final MaterialInboundService inboundService;

    @GetMapping("/page")
    public R<PageResult<BizMaterialInbound>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(inboundService.page(page, size, projectId));
    }

    @GetMapping("/{id}")
    public R<BizMaterialInbound> getById(@PathVariable Long id) {
        return R.ok(inboundService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizMaterialInbound inbound) {
        inboundService.save(inbound, inbound.getDetails() != null ? inbound.getDetails() : List.of());
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMaterialInbound inbound) {
        inbound.setId(id);
        inboundService.update(inbound);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        inboundService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        inboundService.submit(id);
        return R.ok();
    }
}
