package com.zwinsight.contract.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.contract.service.ContractStatisticsService;
import com.zwinsight.contract.vo.ContractAmountSummaryVO;
import com.zwinsight.contract.vo.OutputTrendItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同统计接口（金额汇总 + 产值完成率趋势）
 */
@RestController
@RequestMapping("/api/v1/contract/statistics")
@RequiredArgsConstructor
@RequiresPermission("contract:view")
public class ContractStatisticsController {

    private final ContractStatisticsService contractStatisticsService;

    /**
     * 合同金额汇总（合同金额/变更/产值/开票/收款 + 状态分布）
     */
    @GetMapping("/amount-summary")
    public R<ContractAmountSummaryVO> amountSummary(@RequestParam Long projectId) {
        return R.ok(contractStatisticsService.getAmountSummary(projectId));
    }

    /**
     * 产值完成率趋势（按月聚合已审批产值上报）
     */
    @GetMapping("/output-trend")
    public R<List<OutputTrendItemVO>> outputTrend(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "12") Integer months) {
        return R.ok(contractStatisticsService.getOutputTrend(projectId, months));
    }
}
