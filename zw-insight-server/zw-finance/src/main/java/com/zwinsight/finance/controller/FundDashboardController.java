package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.service.FundDashboardService;
import com.zwinsight.finance.vo.FundDashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 老板资金看板接口（核心三大指标：垫资 / 现金流 / 回款率）
 */
@RestController
@RequestMapping("/api/v1/finance/fund-dashboard")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class FundDashboardController {

    private final FundDashboardService fundDashboardService;

    /**
     * 获取资金看板（projectId 不传时为公司整体聚合）
     */
    @GetMapping
    public R<FundDashboardVO> getDashboard(@RequestParam(required = false) Long projectId) {
        return R.ok(fundDashboardService.getDashboard(projectId));
    }
}
