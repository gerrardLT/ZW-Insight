package com.zwinsight.site.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.site.domain.BizConstructionLog;
import com.zwinsight.site.service.ConstructionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 施工日志接口
 */
@RestController
@RequestMapping("/api/v1/site/construction-log")
@RequiredArgsConstructor
public class ConstructionLogController {

    private final ConstructionLogService logService;

    @GetMapping("/page")
    public R<PageResult<BizConstructionLog>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(logService.page(page, size, projectId, startDate, endDate));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizConstructionLog log) {
        logService.save(log);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizConstructionLog log) {
        log.setId(id);
        logService.update(log);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        logService.delete(id);
        return R.ok();
    }
}
