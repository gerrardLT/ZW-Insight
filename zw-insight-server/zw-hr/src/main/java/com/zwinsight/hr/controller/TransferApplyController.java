package com.zwinsight.hr.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.hr.domain.BizTransferApply;
import com.zwinsight.hr.service.TransferApplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 调动申请接口
 */
@RestController
@RequestMapping("/api/v1/hr/transfer-apply")
@RequiredArgsConstructor
public class TransferApplyController {

    private final TransferApplyService transferApplyService;

    @GetMapping
    public R<PageResult<BizTransferApply>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(transferApplyService.page(page, size));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizTransferApply apply) {
        transferApplyService.save(apply);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        transferApplyService.submit(id);
        return R.ok();
    }
}
