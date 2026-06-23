package com.zwinsight.site.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 质量安全检查接口
 */
@RestController
@RequestMapping("/api/v1/site/inspection")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    @GetMapping
    public R<PageResult<BizInspection>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String inspectionType) {
        return R.ok(inspectionService.page(page, size, projectId, inspectionType));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizInspection inspection) {
        inspectionService.save(inspection);
        return R.ok();
    }

    @PostMapping("/{id}/assign")
    public R<Void> assignRectification(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Long responsiblePersonId = Long.valueOf(params.get("responsiblePersonId").toString());
        LocalDate deadline = LocalDate.parse(params.get("rectificationDeadline").toString());
        inspectionService.assignRectification(id, responsiblePersonId, deadline);
        return R.ok();
    }
}
