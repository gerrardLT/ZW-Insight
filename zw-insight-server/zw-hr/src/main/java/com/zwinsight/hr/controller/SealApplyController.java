package com.zwinsight.hr.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.hr.domain.BizSealApply;
import com.zwinsight.hr.service.SealApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用印申请接口
 */
@RestController
@RequestMapping("/api/v1/hr/seal-apply")
@RequiredArgsConstructor
public class SealApplyController {

    private final SealApplyService sealApplyService;

    @GetMapping
    public R<PageResult<BizSealApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(sealApplyService.page(page, size));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizSealApply apply) {
        sealApplyService.save(apply);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        sealApplyService.submit(id);
        return R.ok();
    }
}
