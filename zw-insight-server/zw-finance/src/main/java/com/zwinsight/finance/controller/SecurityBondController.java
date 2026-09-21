package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizSecurityBond;
import com.zwinsight.finance.service.SecurityBondService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 保证金台账接口（四类全生命周期）
 */
@RestController
@RequestMapping("/api/v1/finance/security-bond")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class SecurityBondController {

    private final SecurityBondService securityBondService;

    /**
     * 分页查询保证金台账
     */
    @GetMapping("/page")
    public R<PageResult<BizSecurityBond>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String bondType,
            @RequestParam(required = false) String refundStatus) {
        return R.ok(securityBondService.page(page, size, projectId, bondType, refundStatus));
    }

    /**
     * 新增保证金记录（含四类比例上限校验）
     */
    @PostMapping
    public R<Void> save(@RequestBody BizSecurityBond bond) {
        securityBondService.save(bond);
        return R.ok();
    }

    /**
     * 发起退还申请
     */
    @PostMapping("/{id}/refund-apply")
    public R<Void> refundApply(@PathVariable Long id) {
        securityBondService.refundApply(id);
        return R.ok();
    }

    /**
     * 确认退还完成
     */
    @PostMapping("/{id}/refund-confirm")
    public R<Void> refundConfirm(
            @PathVariable Long id,
            @RequestParam BigDecimal refundAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refundDate) {
        securityBondService.refundConfirm(id, refundAmount, refundDate);
        return R.ok();
    }

    /**
     * 标记动用（仅工资类，动用后10个工作日内须补足）
     */
    @PostMapping("/{id}/mark-used")
    public R<Void> markUsed(@PathVariable Long id) {
        securityBondService.markUsed(id);
        return R.ok();
    }

    /**
     * 查询即将到期保证金（N 天内）
     */
    @GetMapping("/expiring")
    public R<List<BizSecurityBond>> expiring(@RequestParam(defaultValue = "30") int days) {
        return R.ok(securityBondService.expiring(days));
    }

    /**
     * 保证金占用统计（各类型占用 + 保函替代率，老板看板数据源）
     */
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(@RequestParam(required = false) Long projectId) {
        return R.ok(securityBondService.statistics(projectId));
    }
}
