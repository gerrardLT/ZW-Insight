package com.zwinsight.hr.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.hr.domain.BizEntryApply;
import com.zwinsight.hr.service.EntryApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 入职申请接口
 */
@RestController
@RequestMapping("/api/v1/hr/entry-apply")
@RequiredArgsConstructor
public class EntryApplyController {

    private final EntryApplyService entryApplyService;

    @GetMapping
    public R<PageResult<BizEntryApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String realName) {
        return R.ok(entryApplyService.page(page, size, realName));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizEntryApply apply) {
        entryApplyService.save(apply);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        entryApplyService.submit(id);
        return R.ok();
    }
}
