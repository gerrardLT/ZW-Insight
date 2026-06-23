package com.zwinsight.material.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.material.domain.BizMaterialInbound;
import com.zwinsight.material.domain.BizMaterialInboundDetail;
import com.zwinsight.material.service.MaterialInboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 材料入库接口
 */
@RestController
@RequestMapping("/api/v1/material/inbound")
@RequiredArgsConstructor
public class InboundController {

    private final MaterialInboundService inboundService;

    @GetMapping
    public R<PageResult<BizMaterialInbound>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(inboundService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody Map<String, Object> body) {
        BizMaterialInbound inbound = new BizMaterialInbound();
        // 由前端传入inbound和details，这里通过Map方式接收后手动绑定
        // 实际项目中建议使用专用DTO
        inboundService.save(inbound, List.of());
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        inboundService.submit(id);
        return R.ok();
    }
}
