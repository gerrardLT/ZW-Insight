package com.zwinsight.tender.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.service.TenderRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 投标登记接口
 */
@RestController
@RequestMapping("/api/v1/tender/register")
@RequiredArgsConstructor
public class TenderRegisterController {

    private final TenderRegisterService registerService;

    @GetMapping
    public R<PageResult<BizTenderRegister>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(registerService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizTenderRegister register) {
        registerService.save(register);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<BizTenderRegister> getById(@PathVariable Long id) {
        return R.ok(registerService.getById(id));
    }
}
