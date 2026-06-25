package com.zwinsight.labor.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.labor.service.SalaryStatisticsService;
import com.zwinsight.labor.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 薪资统计接口
 */
@RestController
@RequestMapping("/api/v1/labor/salary")
@RequiredArgsConstructor
public class SalaryStatisticsController {

    private final SalaryStatisticsService salaryStatisticsService;

    /**
     * 薪资统计汇总（按班组）
     */
    @GetMapping("/stats")
    public R<SalaryStatsSummary> getStatsByTeam(
            @RequestParam Long projectId,
            @RequestParam String month) {
        return R.ok(salaryStatisticsService.getStatsByTeam(projectId, month));
    }

    /**
     * 班组薪资明细
     */
    @GetMapping("/detail")
    public R<PageResult<SalaryDetailVO>> getTeamDetail(
            @RequestParam Long projectId,
            @RequestParam String month,
            @RequestParam Long teamId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(salaryStatisticsService.getTeamDetail(projectId, month, teamId, page, size));
    }

    /**
     * 月度报表
     */
    @GetMapping("/report")
    public R<SalaryMonthlyReport> getMonthlyReport(
            @RequestParam Long projectId,
            @RequestParam String month) {
        return R.ok(salaryStatisticsService.generateMonthlyReport(projectId, month));
    }

    /**
     * Excel 导出
     */
    @GetMapping("/export")
    public void exportReport(
            @RequestParam Long projectId,
            @RequestParam String month,
            HttpServletResponse response) {
        salaryStatisticsService.exportReport(projectId, month, response);
    }

    /**
     * 同比环比数据
     */
    @GetMapping("/compare")
    public R<SalaryCompareVO> getCompareData(
            @RequestParam Long projectId,
            @RequestParam String month) {
        return R.ok(salaryStatisticsService.getCompareData(projectId, month));
    }
}
