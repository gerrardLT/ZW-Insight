package com.zwinsight.subcontract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.service.SubcontractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 分包合同接口
 */
@RestController
@RequestMapping("/api/v1/subcontract/contract")
@RequiredArgsConstructor
public class SubcontractController {

    private final SubcontractService subcontractService;

    @GetMapping
    public R<PageResult<BizSubcontract>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(subcontractService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizSubcontract contract) {
        subcontractService.save(contract);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        subcontractService.submit(id);
        return R.ok();
    }
}
