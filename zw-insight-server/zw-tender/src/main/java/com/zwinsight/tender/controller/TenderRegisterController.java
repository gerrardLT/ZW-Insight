package com.zwinsight.tender.controller;

import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.tender.domain.BizTenderPersonBinding;
import com.zwinsight.tender.domain.BizTenderRegister;
import com.zwinsight.tender.service.TenderPersonBindingService;
import com.zwinsight.tender.service.TenderRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 投标登记接口（P1-M2 扩展：人员押证绑定端点 B1/TI-2）
 */
@RestController
@RequestMapping("/api/v1/tender/register")
@RequiredArgsConstructor
@RequiresPermission("tender:view")
public class TenderRegisterController {

    private final TenderRegisterService registerService;
    private final TenderPersonBindingService personBindingService;

    @GetMapping("/page")
    public R<PageResult<BizTenderRegister>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String lostReasonCategory) {
        return R.ok(registerService.page(page, size, projectId, lostReasonCategory));
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

    @RequestMapping(value = "/{id}/submit", method = {RequestMethod.POST, RequestMethod.PUT})
    public R<Void> submit(@PathVariable Long id) {
        registerService.submit(id);
        return R.ok();
    }

    // ================= P1-M2 人员押证绑定（B1 / TI-2） =================

    /** 查询报名下的押证绑定列表 */
    @GetMapping("/{id}/person-bindings")
    public R<List<BizTenderPersonBinding>> listPersonBindings(@PathVariable Long id) {
        return R.ok(personBindingService.listByRegister(id));
    }

    /** 批量绑定押证人员（含 TI-2 排他校验） */
    @PostMapping("/{id}/person-bindings")
    public R<Void> bindPersons(@PathVariable Long id, @RequestBody List<BizTenderPersonBinding> bindings) {
        personBindingService.bindBatch(id, bindings);
        return R.ok();
    }

    /** 单个绑定押证人员 */
    @PostMapping("/{id}/person-bindings/single")
    public R<Void> bindPerson(@PathVariable Long id, @RequestBody BizTenderPersonBinding binding) {
        personBindingService.bind(id, binding);
        return R.ok();
    }

    /** 查询证件的当前活跃锁定（排他校验查询） */
    @GetMapping("/person-bindings/cert/{certId}/active-locks")
    public R<List<BizTenderPersonBinding>> activeLocksByCert(@PathVariable Long certId) {
        return R.ok(personBindingService.listActiveLocksByCert(certId));
    }
}
