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
}
