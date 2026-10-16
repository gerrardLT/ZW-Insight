package com.zwinsight.contract.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.contract.domain.BizContractPayTerm;
import com.zwinsight.contract.service.ContractPayTermService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 合同收付款条款接口（V2026_109，C2）。
 * <p>通用挂接各类合同：contractCategory=CONSTRUCTION/PURCHASE/LABOR/MACHINE/SUBCONTRACT/OTHER + contractId。</p>
 */
@RestController
@RequestMapping("/api/v1/contract/pay-term")
@RequiredArgsConstructor
@RequiresPermission("contract:view")
public class ContractPayTermController {

    private final ContractPayTermService payTermService;

    @GetMapping
    public R<List<BizContractPayTerm>> list(
            @RequestParam String contractCategory,
            @RequestParam Long contractId) {
        return R.ok(payTermService.list(contractCategory, contractId));
    }

    @PostMapping
    @RequiresPermission("contract:contract:edit")
    public R<Void> save(@RequestBody BizContractPayTerm term) {
        payTermService.save(term);
        return R.ok();
    }

    @PutMapping("/{id}")
    @RequiresPermission("contract:contract:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody BizContractPayTerm term) {
        term.setId(id);
        payTermService.update(term);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("contract:contract:edit")
    public R<Void> delete(@PathVariable Long id) {
        payTermService.delete(id);
        return R.ok();
    }
}
