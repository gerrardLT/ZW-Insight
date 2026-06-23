package com.zwinsight.labor.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.labor.domain.BizLaborOutputReport;
import com.zwinsight.labor.service.LaborOutputReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 劳务产值报告接口
 */
@RestController
@RequestMapping("/api/v1/labor/output-report")
@RequiredArgsConstructor
public class LaborOutputReportController {

    private final LaborOutputReportService outputReportService;

    @GetMapping
    public R<PageResult<BizLaborOutputReport>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(outputReportService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizLaborOutputReport report) {
        outputReportService.save(report);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        outputReportService.submit(id);
        return R.ok();
    }
}
