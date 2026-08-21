package com.zwinsight.contract.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizContractDetail;
import com.zwinsight.contract.domain.dto.ContractCreateRequest;
import com.zwinsight.contract.service.ConstructionContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 施工合同接口
 */
@RestController
@RequestMapping("/api/v1/contract")
@RequiredArgsConstructor
@RequiresPermission("contract:view")
public class ContractController {

    private final ConstructionContractService contractService;

    @GetMapping("/page")
    public R<PageResult<BizConstructionContract>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return R.ok(contractService.page(page, size, projectId, status));
    }

    @GetMapping("/{id}")
    public R<BizConstructionContract> getById(@PathVariable Long id) {
        return R.ok(contractService.getById(id));
    }

    @PostMapping
    @RequiresPermission("contract:contract:add")
    @OperLog(module = "施工合同", operType = "INSERT", description = "新增施工合同")
    public R<Void> save(@Valid @RequestBody ContractCreateRequest request) {
        contractService.saveFromRequest(request);
        return R.ok();
    }

    @PutMapping("/{id}")
    @RequiresPermission("contract:contract:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ContractCreateRequest request) {
        contractService.updateFromRequest(id, request);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @RequiresPermission("contract:contract:submit")
    @OperLog(module = "施工合同", operType = "UPDATE", description = "提交施工合同审批")
    public R<Void> submit(@PathVariable Long id) {
        contractService.submit(id);
        return R.ok();
    }

    @GetMapping("/{id}/details")
    public R<List<BizContractDetail>> getDetails(@PathVariable Long id) {
        return R.ok(contractService.getDetails(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("contract:contract:delete")
    @OperLog(module = "施工合同", operType = "DELETE", description = "删除施工合同")
    public R<Void> delete(@PathVariable Long id) {
        contractService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/details")
    @RequiresPermission("contract:contract:edit")
    public R<Void> saveDetails(@PathVariable Long id, @RequestBody List<BizContractDetail> details) {
        contractService.saveDetails(id, details);
        return R.ok();
    }
}
