package com.zwinsight.material.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.material.domain.BizMaterialOutbound;
import com.zwinsight.material.domain.BizMaterialOutboundDetail;
import com.zwinsight.material.service.MaterialOutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 材料出库接口
 */
@RestController
@RequestMapping("/api/v1/material/outbound")
@RequiredArgsConstructor
public class OutboundController {

    private final MaterialOutboundService outboundService;

    @GetMapping("/page")
    public R<PageResult<BizMaterialOutbound>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String outboundType) {
        return R.ok(outboundService.page(page, size, projectId, outboundType));
    }

    @GetMapping("/{id}")
    public R<BizMaterialOutbound> getById(@PathVariable Long id) {
        return R.ok(outboundService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizMaterialOutbound outbound) {
        outboundService.save(outbound, outbound.getDetails() != null ? outbound.getDetails() : List.of());
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMaterialOutbound outbound) {
        outbound.setId(id);
        outboundService.update(outbound);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        outboundService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        outboundService.submit(id);
        return R.ok();
    }
}
