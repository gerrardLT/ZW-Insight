package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.service.FinanceStatisticsService;
import com.zwinsight.finance.vo.CollectionRateVO;
import com.zwinsight.finance.vo.FundPlanItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 财务统计接口（回款率分析 + 资金计划）
 */
@RestController
@RequestMapping("/api/v1/finance/statistics")
@RequiredArgsConstructor
@RequiresPermission("finance:view")
public class FinanceStatisticsController {

    private final FinanceStatisticsService financeStatisticsService;

    /**
     * 回款率分析（已回款对比已开票）
     */
    @GetMapping("/collection-rate")
    public R<CollectionRateVO> collectionRate(@RequestParam Long projectId) {
        return R.ok(financeStatisticsService.getCollectionRate(projectId));
    }

    /**
     * 资金计划（按月应付预测，聚合已审批付款申请）
     */
    @GetMapping("/fund-plan")
    public R<List<FundPlanItemVO>> fundPlan(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "6") Integer months) {
        return R.ok(financeStatisticsService.getFundPlan(projectId, months));
    }
}
