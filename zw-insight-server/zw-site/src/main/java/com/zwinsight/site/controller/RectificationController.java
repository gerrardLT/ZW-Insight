package com.zwinsight.site.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.site.domain.BizRectification;
import com.zwinsight.site.service.RectificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 整改管理接口
 */
@RestController
@RequestMapping("/api/v1/site/rectification")
@RequiredArgsConstructor
@RequiresPermission("site:view")
public class RectificationController {

    private final RectificationService rectificationService;

    @GetMapping("/by-inspection/{inspectionId}")
    public R<List<BizRectification>> listByInspection(@PathVariable Long inspectionId) {
        return R.ok(rectificationService.listByInspection(inspectionId));
    }

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
