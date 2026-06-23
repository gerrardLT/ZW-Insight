package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.finance.domain.BizPersonalReimbursement;
import com.zwinsight.finance.service.PersonalReimbursementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 个人报销接口
 */
@RestController
@RequestMapping("/api/v1/finance/personal-reimbursement")
@RequiredArgsConstructor
public class PersonalReimbursementController {

    private final PersonalReimbursementService personalReimbursementService;

    @GetMapping
    public R<PageResult<BizPersonalReimbursement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(personalReimbursementService.page(page, size));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizPersonalReimbursement reimbursement) {
        personalReimbursementService.save(reimbursement);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        personalReimbursementService.submit(id);
        return R.ok();
    }
}
