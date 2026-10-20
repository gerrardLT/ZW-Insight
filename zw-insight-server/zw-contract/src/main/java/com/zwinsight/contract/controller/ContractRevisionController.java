package com.zwinsight.contract.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.contract.domain.ContractRevision;
import com.zwinsight.contract.domain.dto.ContractRevisionRequest;
import com.zwinsight.contract.service.ContractRevisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 合同修订接口（V2026_123 起覆盖施工合同与四类支出合同）。
 * <p>权限（V2026_125）：读接口按五类模块 view 码 OR 放行；写接口（新建/作废/提交）
 * 按细粒度写码放行（施工 contract:contract:edit/submit，支出四类 purchase/labor/machine/subcontract:contract:edit）。
 * 服务层再按 contractCategory 做二次校验，防止仅持 A 模块写权限的用户操作 B 模块修订。</p>
 */
@RestController
@RequestMapping("/api/v1/contract/revision")
@RequiredArgsConstructor
@RequiresPermission({"contract:view", "purchase:view", "labor:view", "machine:view", "subcontract:view"})
public class ContractRevisionController {

    private final ContractRevisionService service;

    @GetMapping
    public R<List<ContractRevision>> list(@RequestParam Long contractId,
                                          @RequestParam(required = false) String contractCategory) {
        return R.ok(service.list(contractId, contractCategory));
    }

    @GetMapping("/{id}")
    public R<ContractRevision> detail(@PathVariable Long id) {
        return R.ok(service.detailForUser(id));
    }

    @PostMapping
    @RequiresPermission({"contract:contract:edit", "purchase:contract:edit", "labor:contract:edit",
            "machine:contract:edit", "subcontract:contract:edit"})
    public R<Long> create(@Valid @RequestBody ContractRevisionRequest request) {
        return R.ok(service.create(request));
    }

    @PostMapping("/{id}/cancel")
    @RequiresPermission({"contract:contract:edit", "purchase:contract:edit", "labor:contract:edit",
            "machine:contract:edit", "subcontract:contract:edit"})
    public R<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @RequiresPermission({"contract:contract:submit", "purchase:contract:edit", "labor:contract:edit",
            "machine:contract:edit", "subcontract:contract:edit"})
    public R<Void> submit(@PathVariable Long id) {
        service.submit(id);
        return R.ok();
    }
}
