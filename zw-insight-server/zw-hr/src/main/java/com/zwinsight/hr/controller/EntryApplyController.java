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

    @GetMapping("/page")
    public R<PageResult<BizEntryApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String realName) {
        return R.ok(entryApplyService.page(page, size, realName));
    }

    @GetMapping("/{id}")
    public R<BizEntryApply> getById(@PathVariable Long id) {
        return R.ok(entryApplyService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizEntryApply apply) {
        entryApplyService.save(apply);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizEntryApply apply) {
        apply.setId(id);
        entryApplyService.update(apply);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        entryApplyService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        entryApplyService.submit(id);
        return R.ok();
    }
}
