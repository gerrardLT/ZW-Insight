package com.zwinsight.labor.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.labor.service.LaborStatisticsService;
import com.zwinsight.labor.vo.LaborCostRatioVO;
import com.zwinsight.labor.vo.PayrollTrendItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 劳务统计接口（工资发放趋势 + 劳务成本占比）
 */
@RestController
@RequestMapping("/api/v1/labor/statistics")
@RequiredArgsConstructor
@RequiresPermission("labor:view")
public class LaborStatisticsController {

    private final LaborStatisticsService laborStatisticsService;

    /**
     * 工资发放趋势（按月聚合结算/已付/未付）
     */
    @GetMapping("/payroll-trend")
    public R<List<PayrollTrendItemVO>> payrollTrend(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "12") Integer months) {
        return R.ok(laborStatisticsService.getPayrollTrend(projectId, months));
    }

    /**
     * 劳务成本占比（结算总额对比生效劳务合同金额）
     */
    @GetMapping("/cost-ratio")
    public R<LaborCostRatioVO> costRatio(@RequestParam Long projectId) {
        return R.ok(laborStatisticsService.getCostRatio(projectId));
    }
}
