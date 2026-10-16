package com.zwinsight.contract.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.contract.domain.BizFinalSettlement;
import com.zwinsight.contract.service.FinalSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 合同结算接口（2026-10-16 扩展：PROCESS 过程结算 / FINAL 竣工结算）
 */
@RestController
@RequestMapping("/api/v1/contract/settlement")
@RequiredArgsConstructor
@RequiresPermission("contract:view")
public class FinalSettlementController {

    private final FinalSettlementService finalSettlementService;

    @GetMapping
    public R<PageResult<BizFinalSettlement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String settlementType) {
        return R.ok(finalSettlementService.page(page, size, projectId, contractId, settlementType));
    }

    @GetMapping("/{id}")
    public R<BizFinalSettlement> detail(@PathVariable Long id) {
        return R.ok(finalSettlementService.getDetail(id));
    }

    @PostMapping
    @RequiresPermission("contract:settlement:add")
    public R<Void> save(@RequestBody BizFinalSettlement settlement) {
        finalSettlementService.save(settlement);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @RequiresPermission("contract:settlement:submit")
    public R<Void> submit(@PathVariable Long id) {
        finalSettlementService.submit(id);
        return R.ok();
    }

    /**
     * 可选来源（过程结算用）：本合同下已审批且未被有效结算占用的产值报告与签证。
     */
    @GetMapping("/available-sources")
    public R<Map<String, List<Map<String, Object>>>> availableSources(@RequestParam Long contractId) {
        return R.ok(finalSettlementService.listAvailableSources(contractId));
    }
}
