package com.zwinsight.site.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.site.domain.BizRectification;
import com.zwinsight.site.service.RectificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 整改管理接口
 */
@RestController
@RequestMapping("/api/v1/site/rectification")
@RequiredArgsConstructor
public class RectificationController {

    private final RectificationService rectificationService;

    @PostMapping("/{inspectionId}/submit")
    public R<Void> submit(@PathVariable Long inspectionId, @RequestBody BizRectification rectification) {
        rectificationService.submit(inspectionId, rectification);
        return R.ok();
    }

    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        rectificationService.approve(id);
        return R.ok();
    }
}
