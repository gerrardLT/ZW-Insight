package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizFundAnnualBudget;
import com.zwinsight.finance.domain.BizFundMonthlyPlan;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.service.FundPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资金计划接口（三层联动：年度预算 / 月度计划 / 滚动预测）
 */
@RestController
@RequestMapping("/api/v1/finance/fund-plan")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class FundPlanController {

    private final FundPlanService fundPlanService;

    // ==================== 年度预算 ====================

    /**
     * 新增年度预算（项目+年度唯一）
     */
    @PostMapping("/annual")
    public R<Void> saveAnnualBudget(@RequestBody BizFundAnnualBudget budget) {
        fundPlanService.saveAnnualBudget(budget);
        return R.ok();
    }

    /**
     * 分页查询年度预算
     */
    @GetMapping("/annual/page")
    public R<PageResult<BizFundAnnualBudget>> pageAnnualBudget(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer budgetYear,
            @RequestParam(required = false) Long projectId) {
        return R.ok(fundPlanService.pageAnnualBudget(page, size, budgetYear, projectId));
    }

    // ==================== 月度计划 ====================

    /**
     * 保存月度计划（同项目同年月存在即更新）
     */
    @PostMapping("/monthly")
    public R<Void> saveMonthlyPlan(@RequestBody BizFundMonthlyPlan plan) {
        fundPlanService.saveMonthlyPlan(plan);
        return R.ok();
    }

    /**
     * 分页查询月度计划
     */
    @GetMapping("/monthly/page")
    public R<PageResult<BizFundMonthlyPlan>> pageMonthlyPlan(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer planYear,
            @RequestParam(required = false) Long projectId) {
        return R.ok(fundPlanService.pageMonthlyPlan(page, size, planYear, projectId));
    }

    /**
     * 回填月度实际收支（真实单据聚合：回款登记 + 已审批付款申请）
     */
    @PostMapping("/monthly/fill-actual")
    public R<Void> fillActual(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Long projectId) {
        fundPlanService.fillActual(year, month, projectId);
        return R.ok();
    }

    // ==================== 滚动预测 ====================

    /**
     * 生成滚动预测快照（未来 N 个月，1-12）
     */
    @PostMapping("/rolling/generate")
    public R<List<BizFundRollingForecast>> generateRollingForecast(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "6") int months) {
        return R.ok(fundPlanService.generateRollingForecast(projectId, months));
    }

    /**
     * 查询最新预测快照（按月倒序）
     */
    @GetMapping("/rolling/page")
    public R<PageResult<BizFundRollingForecast>> pageRollingForecast(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) Long projectId) {
        return R.ok(fundPlanService.pageRollingForecast(page, size, projectId));
    }
}
