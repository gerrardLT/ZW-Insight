package com.zwinsight.hr.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.hr.domain.BizResignApply;
import com.zwinsight.hr.service.ResignApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 离职申请接口
 */
@RestController
@RequestMapping("/api/v1/hr/resign-apply")
@RequiredArgsConstructor
public class ResignApplyController {

    private final ResignApplyService resignApplyService;

    @GetMapping
    public R<PageResult<BizResignApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(resignApplyService.page(page, size));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizResignApply apply) {
        resignApplyService.save(apply);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        resignApplyService.submit(id);
        return R.ok();
    }
}
