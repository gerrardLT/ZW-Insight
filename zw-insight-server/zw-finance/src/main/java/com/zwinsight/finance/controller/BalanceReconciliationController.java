package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizBalanceReconciliation;
import com.zwinsight.finance.service.BalanceReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 银行存款余额调节表接口
 */
@RestController
@RequestMapping("/api/v1/finance/balance-reconciliation")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class BalanceReconciliationController {

    private final BalanceReconciliationService reconciliationService;

    /**
     * 分页查询调节表
     */
    @GetMapping("/page")
    public R<PageResult<BizBalanceReconciliation>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return R.ok(reconciliationService.page(page, size, accountId, start, end));
    }

    /**
     * 生成/更新调节表（自动计算调节后余额与调平状态）
     */
    @PostMapping
    public R<BizBalanceReconciliation> save(@RequestBody BizBalanceReconciliation record) {
        return R.ok(reconciliationService.save(record));
    }

    /**
     * 调节表明细（含实时计算的调节后余额）
     */
    @GetMapping("/{id}")
    public R<BizBalanceReconciliation> detail(@PathVariable Long id) {
        return R.ok(reconciliationService.detail(id));
    }
}
