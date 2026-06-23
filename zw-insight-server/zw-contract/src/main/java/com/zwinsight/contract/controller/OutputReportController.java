package com.zwinsight.contract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.service.OutputReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 产值报告接口
 */
@RestController
@RequestMapping("/api/v1/contract/output")
@RequiredArgsConstructor
public class OutputReportController {

    private final OutputReportService outputReportService;

    @GetMapping
    public R<PageResult<BizOutputReport>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(outputReportService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizOutputReport report) {
        outputReportService.save(report);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        outputReportService.submit(id);
        return R.ok();
    }
}
