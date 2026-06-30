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

    @GetMapping("/page")
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

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizInspection inspection) {
        inspection.setId(id);
        inspectionService.update(inspection);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        inspectionService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/results")
    public R<Void> submitResults(@PathVariable Long id, @RequestBody Map<String, Object> results) {
        inspectionService.submitResults(id, results);
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
