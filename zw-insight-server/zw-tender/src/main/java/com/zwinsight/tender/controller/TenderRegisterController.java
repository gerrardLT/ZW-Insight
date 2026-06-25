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
    @GetMapping("/page")
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

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizTenderRegister register) {
        register.setId(id);
        registerService.update(register);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        registerService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        registerService.submit(id);
        return R.ok();
    }
}
