package com.zwinsight.labor.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.service.LaborContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 劳务合同接口
 */
@RestController
@RequestMapping("/api/v1/labor/contract")
@RequiredArgsConstructor
public class LaborContractController {

    private final LaborContractService laborContractService;

    @GetMapping
    public R<PageResult<BizLaborContract>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(laborContractService.page(page, size, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizLaborContract contract) {
        laborContractService.save(contract);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        laborContractService.submit(id);
        return R.ok();
    }
}
