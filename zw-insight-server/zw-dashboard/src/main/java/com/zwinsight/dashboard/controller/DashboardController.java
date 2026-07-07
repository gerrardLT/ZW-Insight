package com.zwinsight.dashboard.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * 数据看板接口
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 公司概览
     */
    @GetMapping("/company-overview")
    public R<Map<String, Object>> getCompanyOverview() {
        return R.ok(dashboardService.getCompanyOverview());
    }

    /**
     * 预算执行
     */
    @GetMapping("/budget-execution")
    public R<Map<String, Object>> getBudgetExecution(
            @RequestParam Long projectId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return R.ok(dashboardService.getBudgetExecution(projectId, startDate, endDate));
    }

    /**
     * 应收款监控
     */
    @GetMapping("/receivable-monitor")
    public R<Map<String, Object>> getReceivableMonitor() {
        return R.ok(dashboardService.getReceivableMonitor());
    }

    /**
     * 供应商应付监控
     */
    @GetMapping("/supplier-payable")
    public R<Map<String, Object>> getSupplierPayableMonitor(
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String supplierName) {
        return R.ok(dashboardService.getSupplierPayableMonitor(projectName, supplierName));
    }

    /**
     * 投标分析
     */
    @GetMapping("/tender-analysis")
    public R<Map<String, Object>> getTenderAnalysis() {
        return R.ok(dashboardService.getTenderAnalysis());
    }

    /**
     * 库存分析
     */
    @GetMapping("/inventory-analysis")
    public R<Map<String, Object>> getInventoryAnalysis() {
        return R.ok(dashboardService.getInventoryAnalysis());
    }

    /**
     * 进度甘特图
     */
    @GetMapping("/schedule-gantt/{projectId}")
    public R<Map<String, Object>> getScheduleGantt(@PathVariable Long projectId) {
        return R.ok(dashboardService.getScheduleGantt(projectId));
    }

    /**
     * 项目级看板（进度+质安+资金一屏聚合）
     */
    @GetMapping("/project/{projectId}")
    public R<Map<String, Object>> getProjectDashboard(@PathVariable Long projectId) {
        return R.ok(dashboardService.getProjectDashboard(projectId));
    }

    /**
     * 预算偏差分析（计划 vs 实际 按科目对比）
     */
    @GetMapping("/budget-variance")
    public R<Map<String, Object>> getBudgetVariance(@RequestParam Long projectId) {
        return R.ok(dashboardService.getBudgetVariance(projectId));
    }

    /**
     * 利润趋势分析（按月展示收入/支出/利润曲线）
     *
     * @param year 年份（默认当年）
     */
    @GetMapping("/profit-trend")
    public R<Map<String, Object>> getProfitTrend(
            @RequestParam(required = false) Integer year) {
        return R.ok(dashboardService.getProfitTrend(year));
    }

    /**
     * 项目排名（按产值/利润率/回款率 TopN 排名）
     *
     * @param rankBy 排名维度（output-产值/profitRate-利润率/receivedRate-回款率）
     * @param topN   展示前N个（默认10）
     */
    @GetMapping("/project-ranking")
    public R<Map<String, Object>> getProjectRanking(
            @RequestParam(defaultValue = "output") String rankBy,
            @RequestParam(defaultValue = "10") int topN) {
        return R.ok(dashboardService.getProjectRanking(rankBy, topN));
    }

    /**
     * 发票台账（进销项统一汇总视图）
     *
     * @param projectId 项目ID（可选）
     * @param month     月份 yyyy-MM（可选）
     */
    @GetMapping("/invoice-ledger")
    public R<Map<String, Object>> getInvoiceLedger(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String month) {
        return R.ok(dashboardService.getInvoiceLedger(projectId, month));
    }

    /**
     * 人事统计（在职/部门/年龄分布）
     */
    @GetMapping("/hr-statistics")
    public R<Map<String, Object>> getHrStatistics() {
        return R.ok(dashboardService.getHrStatistics());
    }
}
