package com.zwinsight.hr.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.hr.domain.BizRegularApply;
import com.zwinsight.hr.service.RegularApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 转正申请接口
 */
@RestController
@RequestMapping("/api/v1/hr/regular-apply")
@RequiredArgsConstructor
public class RegularApplyController {

    private final RegularApplyService regularApplyService;

    @GetMapping
    public R<PageResult<BizRegularApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(regularApplyService.page(page, size));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizRegularApply apply) {
        regularApplyService.save(apply);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        regularApplyService.submit(id);
        return R.ok();
    }
}
