package com.zwinsight.material.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.material.domain.BizInbound;
import com.zwinsight.material.domain.BizOutbound;
import com.zwinsight.material.service.InboundService;
import com.zwinsight.material.service.OutboundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 材料管理接口 - 入库与出库
 */
@RestController
@RequestMapping(value = "/api/v1/material")
@RequiredArgsConstructor
public class MaterialController {

    private final InboundService inboundService;
    private final OutboundService outboundService;

    // ======================== 入库 ========================

    @GetMapping(value = "/inbound/page")
    public R<PageResult<BizInbound>> getInboundPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String materialName) {
        return R.ok(inboundService.page(page, size, projectId, materialName));
    }

    @GetMapping(value = "/inbound/{id}")
    public R<BizInbound> getInboundDetail(@PathVariable Long id) {
        return R.ok(inboundService.getById(id));
    }

    @PostMapping(value = "/inbound")
    public R<Void> createInbound(@Valid @RequestBody BizInbound data) {
        inboundService.create(data);
        return R.ok();
    }

    @PutMapping(value = "/inbound/{id}")
    public R<Void> updateInbound(@PathVariable Long id, @Valid @RequestBody BizInbound data) {
        data.setId(id);
        inboundService.update(data);
        return R.ok();
    }

    @DeleteMapping(value = "/inbound/{id}")
    public R<Void> deleteInbound(@PathVariable Long id) {
        inboundService.delete(id);
        return R.ok();
    }

    // ======================== 出库 ========================

    @GetMapping("/outbound/page")
    public R<PageResult<BizOutbound>> getOutboundPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(outboundService.page(page, size, projectId));
    }

    @PostMapping("/outbound")
    public R<Void> createOutbound(@Valid @RequestBody BizOutbound data) {
        outboundService.create(data);
        return R.ok();
    }

    @DeleteMapping("/outbound/{id}")
    public R<Void> deleteOutbound(@PathVariable Long id) {
        outboundService.delete(id);
        return R.ok();
    }
}
