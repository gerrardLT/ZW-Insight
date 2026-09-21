package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizBankFlow;
import com.zwinsight.finance.service.BankFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 银行流水与余额登记接口（资金日报头寸数据源）
 */
@RestController
@RequestMapping("/api/v1/finance/bank-flow")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class BankFlowController {

    private final BankFlowService bankFlowService;

    // ==================== 余额登记 ====================

    /**
     * 登记/更新账户余额
     */
    @PostMapping("/balance")
    public R<Void> recordBalance(
            @RequestParam Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate snapshotDate,
            @RequestParam BigDecimal balance,
            @RequestParam(required = false) String remark) {
        bankFlowService.recordBalance(accountId, snapshotDate, balance, remark);
        return R.ok();
    }

    /**
     * 各账户截至指定日期的最新余额（日报头寸数据源）
     */
    @GetMapping("/latest-balances")
    public R<List<Map<String, Object>>> latestBalances(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return R.ok(bankFlowService.latestBalances(asOfDate != null ? asOfDate : LocalDate.now()));
    }

    // ==================== 流水管理 ====================

    /**
     * 分页查询流水
     */
    @GetMapping("/page")
    public R<PageResult<BizBankFlow>> pageFlows(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Integer reconciled) {
        return R.ok(bankFlowService.pageFlows(page, size, accountId, start, end, reconciled));
    }

    /**
     * 手工登记单笔流水
     */
    @PostMapping
    public R<Void> addFlow(@RequestBody BizBankFlow flow) {
        bankFlowService.addFlow(flow);
        return R.ok();
    }

    /**
     * 批量导入流水（前端解析网银导出文件后提交结构化数据）
     */
    @PostMapping("/import")
    public R<Map<String, Object>> importFlows(@RequestBody List<BizBankFlow> flows) {
        return R.ok(bankFlowService.importFlows(flows));
    }

    /**
     * 勾稽匹配（流水关联内部单据）
     */
    @PostMapping("/{id}/match")
    public R<Void> matchFlow(
            @PathVariable Long id,
            @RequestParam String matchedType,
            @RequestParam Long matchedId) {
        bankFlowService.matchFlow(id, matchedType, matchedId);
        return R.ok();
    }

    /**
     * 取消勾稽
     */
    @PostMapping("/{id}/unmatch")
    public R<Void> unmatchFlow(@PathVariable Long id) {
        bankFlowService.unmatchFlow(id);
        return R.ok();
    }
}
