package com.zwinsight.budget.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.domain.BizCostAccountLink;
import com.zwinsight.budget.domain.BizCostAccountTxn;
import com.zwinsight.budget.service.CostAccountLinkService;
import com.zwinsight.budget.service.CostAccountService;
import com.zwinsight.budget.service.CostLedgerService;
import com.zwinsight.budget.service.CostRollUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 成本账户管理接口（CBS, Cost Breakdown Structure）
 */
@RestController
@RequestMapping("/api/v1/budget/cost-account")
@RequiredArgsConstructor
@RequiresPermission("budget:costaccount:view")
public class CostAccountController {

    private final CostAccountService costAccountService;
    private final CostLedgerService costLedgerService;
    private final CostRollUpService costRollUpService;
    private final CostAccountLinkService costAccountLinkService;

    /**
     * 分页查询成本账户
     */
    @GetMapping("/page")
    public R<PageResult<BizCostAccount>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Long wbsNodeId,
            @RequestParam(required = false) String costCategory,
            @RequestParam(required = false) String status) {
        
        return R.ok(costAccountService.page(page, size, projectId, parentId, wbsNodeId, costCategory, status));
    }

    /**
     * 获取成本账户树形结构
     */
    @GetMapping("/tree")
    public R<List<BizCostAccount>> getTree(@RequestParam Long projectId) {
        return R.ok(costAccountService.getTree(projectId));
    }

    /**
     * 根据 ID 查询账户详情
     */
    @GetMapping("/{id}")
    public R<BizCostAccount> getById(@PathVariable Long id) {
        return R.ok(costAccountService.getById(id));
    }

    /**
     * 新增成本账户
     */
    @PostMapping
    @RequiresPermission("budget:costaccount:add")
    @OperLog(module = "成本管理", operType = "INSERT", description = "新增成本账户")
    public R<BizCostAccount> create(@Valid @RequestBody BizCostAccount request) {
        BizCostAccount created = costAccountService.create(request);
        return R.ok(created);
    }

    /**
     * 更新成本账户
     */
    @PutMapping("/{id}")
    @RequiresPermission("budget:costaccount:update")
    @OperLog(module = "成本管理", operType = "UPDATE", description = "更新成本账户")
    public R<BizCostAccount> update(@PathVariable Long id, @Valid @RequestBody BizCostAccount request) {
        BizCostAccount updated = costAccountService.update(id, request);
        return R.ok(updated);
    }

    /**
     * 删除成本账户（软删除）
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("budget:costaccount:delete")
    @OperLog(module = "成本管理", operType = "DELETE", description = "删除成本账户")
    public R<Void> delete(@PathVariable Long id) {
        costAccountService.delete(id);
        return R.ok();
    }

    /**
     * 锁定成本账户
     */
    @PostMapping("/{id}/lock")
    @RequiresPermission("budget:costaccount:update")
    @OperLog(module = "成本管理", operType = "UPDATE", description = "锁定成本账户")
    public R<Void> lock(@PathVariable Long id) {
        costAccountService.lock(id);
        return R.ok();
    }

    /**
     * 关闭成本账户（归档）
     */
    @PostMapping("/{id}/close")
    @RequiresPermission("budget:costaccount:update")
    @OperLog(module = "成本管理", operType = "UPDATE", description = "关闭成本账户")
    public R<Void> close(@PathVariable Long id) {
        costAccountService.close(id);
        return R.ok();
    }

    /**
     * 从源模块同步金额（合同/采购/材料/劳务等）
     */
    @PostMapping("/{id}/sync")
    @RequiresPermission("budget:costaccount:update")
    @OperLog(module = "成本管理", operType = "UPDATE", description = "同步成本账户金额")
    public R<Void> sync(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal commitmentDelta,
            @RequestParam(required = false) BigDecimal actualDelta) {
        
        costAccountService.syncFromSource(id, commitmentDelta, actualDelta);
        return R.ok();
    }

    /**
     * 成本流水下钻：这个账户的金额是怎么一步步变成现在这样的。
     * <p>
     * 每一条变动都带来源单据（变更事件/合同/结算/付款），
     * 支撑「全链路可追溯」与审计回放。
     * </p>
     *
     * @param id         成本账户ID
     * @param amountType 金额维度过滤（可空=全部）
     * @param from       业务发生时间起（可空）
     * @param to         业务发生时间止（可空）
     */
    @GetMapping("/{id}/ledger")
    public R<List<BizCostAccountTxn>> ledger(
            @PathVariable Long id,
            @RequestParam(required = false) String amountType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return R.ok(costLedgerService.listByAccount(id, amountType, from, to));
    }

    /**
     * 成本流水分页（跨账户查，用于项目级台账审阅）
     */
    @GetMapping("/ledger/page")
    public R<PageResult<BizCostAccountTxn>> ledgerPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String amountType,
            @RequestParam(required = false) String sourceType) {
        return R.ok(PageResult.of(costLedgerService.page(page, size, accountId, amountType, sourceType)));
    }

    // ==================== 成本归集（源单据 → CBS 账户）====================

    /**
     * 执行成本归集：从合同/结算/材料出库自动汇总承诺额与实际成本到 CBS 账户。
     * <p>
     * 可重复执行、可定时执行：幂等键编码「状态跃迁」（from→to），
     * 重跑不会双记，源单据变化会产生新跃迁正常记账。
     * </p>
     * <p>
     * 返回报告包含待绑定单据清单（unmapped）——一个科目多个账户时需人工指定归属，
     * 系统不猜（猜错会导致成本归属错误，进而得出错误的盈亏结论）。
     * </p>
     */
    @PostMapping("/rollup")
    @RequiresPermission("budget:costaccount:sync")
    @OperLog(module = "成本管理", operType = "UPDATE", description = "执行成本归集")
    public R<CostRollUpService.RollupReport> rollup(@RequestParam Long projectId) {
        return R.ok(costRollUpService.rollup(projectId));
    }

    // ==================== 源单据 → 账户 显式绑定 ====================

    /**
     * 查询项目的绑定关系
     */
    @GetMapping("/link/list")
    public R<List<BizCostAccountLink>> listLinks(@RequestParam Long projectId) {
        return R.ok(costAccountLinkService.listByProject(projectId));
    }

    /**
     * 查询某账户的绑定明细（这个账户的钱来自哪些单据）
     */
    @GetMapping("/{id}/link")
    public R<List<BizCostAccountLink>> listLinksByAccount(@PathVariable Long id) {
        return R.ok(costAccountLinkService.listByAccount(id));
    }

    /**
     * 新增绑定（幂等：已存在则返回既有记录）
     */
    @PostMapping("/link")
    @RequiresPermission("budget:costaccount:sync")
    @OperLog(module = "成本管理", operType = "INSERT", description = "绑定源单据到成本账户")
    public R<BizCostAccountLink> bind(@RequestBody BizCostAccountLink request) {
        return R.ok(costAccountLinkService.bind(request));
    }

    /**
     * 批量绑定（归集报告里一次性处理多张待绑定单据）
     */
    @PostMapping("/link/batch")
    @RequiresPermission("budget:costaccount:sync")
    @OperLog(module = "成本管理", operType = "INSERT", description = "批量绑定源单据")
    public R<Integer> bindBatch(@RequestBody List<BizCostAccountLink> requests) {
        return R.ok(costAccountLinkService.bindBatch(requests));
    }

    /**
     * 解除绑定（仅影响下次归集分配，不自动冲销已记账）
     */
    @DeleteMapping("/link/{id}")
    @RequiresPermission("budget:costaccount:sync")
    @OperLog(module = "成本管理", operType = "DELETE", description = "解除成本账户绑定")
    public R<Void> unbind(@PathVariable Long id) {
        costAccountLinkService.unbind(id);
        return R.ok();
    }
}
