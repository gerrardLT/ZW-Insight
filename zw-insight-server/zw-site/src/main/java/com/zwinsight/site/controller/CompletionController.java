package com.zwinsight.site.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.site.domain.BizCompletionAcceptance;
import com.zwinsight.site.service.CompletionAcceptanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 竣工验收接口
 */
@RestController
@RequestMapping("/api/v1/site/completion")
@RequiredArgsConstructor
public class CompletionController {

    private final CompletionAcceptanceService acceptanceService;

    @GetMapping("/page")
    public R<PageResult<BizCompletionAcceptance>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(acceptanceService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizCompletionAcceptance acceptance) {
        acceptanceService.save(acceptance);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        acceptanceService.submit(id);
        return R.ok();
    }
}
