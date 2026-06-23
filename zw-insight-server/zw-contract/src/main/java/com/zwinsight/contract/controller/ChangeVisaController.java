package com.zwinsight.contract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.contract.domain.BizChangeVisa;
import com.zwinsight.contract.service.ChangeVisaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 变更签证接口
 */
@RestController
@RequestMapping("/api/v1/contract/change-visa")
@RequiredArgsConstructor
public class ChangeVisaController {

    private final ChangeVisaService changeVisaService;

    @GetMapping
    public R<PageResult<BizChangeVisa>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String changeType) {
        return R.ok(changeVisaService.page(page, size, projectId, contractId, changeType));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizChangeVisa changeVisa) {
        changeVisaService.save(changeVisa);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        changeVisaService.submit(id);
        return R.ok();
    }
}
