package com.zwinsight.finance.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizFundAnnualBudget;
import com.zwinsight.finance.domain.BizFundMonthlyPlan;
import com.zwinsight.finance.domain.BizFundPlanDetail;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.service.FundPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /**
     * 查询月度计划科目明细（V2026_58）
     */
    @GetMapping("/monthly/{planId}/details")
    public R<List<BizFundPlanDetail>> listPlanDetails(@PathVariable Long planId) {
        return R.ok(fundPlanService.listPlanDetails(planId));
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

    /**
     * 待支付大额支出 TOP（按科目聚合，驾驶舱资金中心消费；V2026_56）
     * <p>V2026_63 起含已逾期部分（原口径下界为 today 会漏掉全部逾期待付款），
     * 返回中另给 overdueAmount 字段区分“其中已逾期”。</p>
     */
    @GetMapping("/rolling/top-expenses")
    public R<List<Map<String, Object>>> futureExpenseTop(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "30") int days) {
        return R.ok(fundPlanService.futureExpenseTop(projectId, days));
    }

    /**
     * 未来 N 天资金预测（驾驶舱 UI §9.2：30/60/90 天三档，累计窗口）
     * <p>返回：预计回款/预计付款（含其中逾期）/资金差额 netFlow/可用资金/缺口 gap/coverable。
     * netFlow 为 §9.2 表格口径（回款−付款），gap 为 §10.4 口径（付款−可用资金），两者不可混用。</p>
     */
    @GetMapping("/rolling/days")
    public R<Map<String, Object>> forecastByDays(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "30") int days) {
        return R.ok(fundPlanService.forecastByDays(projectId, days));
    }

    /**
     * 资金缺口归因（驾驶舱 UI §9.2：“哪个项目导致 + 主要付款对象”）
     */
    @GetMapping("/rolling/gap-attribution")
    public R<Map<String, Object>> gapAttribution(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "10") int topN) {
        return R.ok(fundPlanService.gapAttribution(projectId, days, topN));
    }
}
