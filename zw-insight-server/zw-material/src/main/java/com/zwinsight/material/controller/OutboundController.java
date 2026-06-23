package com.zwinsight.material.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.material.domain.BizMaterialOutbound;
import com.zwinsight.material.domain.BizMaterialOutboundDetail;
import com.zwinsight.material.service.MaterialOutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 材料出库接口
 */
@RestController
@RequestMapping("/api/v1/material/outbound")
@RequiredArgsConstructor
public class OutboundController {

    private final MaterialOutboundService outboundService;

    @GetMapping
    public R<PageResult<BizMaterialOutbound>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String outboundType) {
        return R.ok(outboundService.page(page, size, projectId, outboundType));
    }

    @PostMapping
    public R<Void> save(@RequestBody Map<String, Object> body) {
        BizMaterialOutbound outbound = new BizMaterialOutbound();
        outboundService.save(outbound, List.of());
        return R.ok();
    }
}
