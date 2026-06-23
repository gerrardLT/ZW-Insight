package com.zwinsight.subcontract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.subcontract.domain.BizSubcontractOutputReport;
import com.zwinsight.subcontract.service.SubcontractOutputService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 分包产值接口
 */
@RestController
@RequestMapping("/api/v1/subcontract/output")
@RequiredArgsConstructor
public class SubcontractOutputController {

    private final SubcontractOutputService outputService;

    @GetMapping
    public R<PageResult<BizSubcontractOutputReport>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId) {
        return R.ok(outputService.page(page, size, projectId, contractId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizSubcontractOutputReport report) {
        outputService.save(report);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        outputService.submit(id);
        return R.ok();
    }
}
