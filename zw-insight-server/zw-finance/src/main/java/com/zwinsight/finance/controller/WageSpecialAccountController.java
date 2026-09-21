package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizWageDeposit;
import com.zwinsight.finance.domain.BizWageSpecialAccount;
import com.zwinsight.finance.service.WageSpecialAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 农民工工资专用账户接口（条例合规）
 */
@RestController
@RequestMapping("/api/v1/finance/wage-account")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class WageSpecialAccountController {

    private final WageSpecialAccountService wageSpecialAccountService;

    /**
     * 分页查询专户
     */
    @GetMapping("/page")
    public R<PageResult<BizWageSpecialAccount>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return R.ok(wageSpecialAccountService.page(page, size, projectId, status));
    }

    /**
     * 新增专户（项目维度唯一）
     */
    @PostMapping
    public R<Void> save(@RequestBody BizWageSpecialAccount account) {
        wageSpecialAccountService.save(account);
        return R.ok();
    }

    /**
     * 登记人工费拨付到账（条例第29条）
     */
    @PostMapping("/{id}/deposit")
    public R<Void> recordDeposit(@PathVariable Long id, @RequestBody BizWageDeposit deposit) {
        deposit.setAccountId(id);
        wageSpecialAccountService.recordDeposit(deposit);
        return R.ok();
    }

    /**
     * 登记代发工资（总包代发，条例第31条）
     */
    @PostMapping("/{id}/wage-payment")
    public R<Void> recordWagePayment(
            @PathVariable Long id,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payDate) {
        wageSpecialAccountService.recordWagePayment(id, amount, payDate);
        return R.ok();
    }

    /**
     * 查询专户拨付记录
     */
    @GetMapping("/{id}/deposits")
    public R<List<BizWageDeposit>> deposits(@PathVariable Long id) {
        return R.ok(wageSpecialAccountService.deposits(id));
    }

    /**
     * 人工费到位率（合规指标）
     */
    @GetMapping("/{id}/arrival-rate")
    public R<BigDecimal> arrivalRate(@PathVariable Long id) {
        return R.ok(wageSpecialAccountService.arrivalRate(id));
    }

    /**
     * 合规巡检（手动触发；定时任务入口同方法）
     */
    @PostMapping("/compliance-scan")
    public R<List<BizWageSpecialAccount>> complianceScan() {
        return R.ok(wageSpecialAccountService.complianceScan());
    }

    /**
     * 销户（留痕）
     */
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        wageSpecialAccountService.cancel(id);
        return R.ok();
    }
}
