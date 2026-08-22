package com.zwinsight.labor.service;

import com.zwinsight.labor.vo.LaborCostRatioVO;
import com.zwinsight.labor.vo.PayrollTrendItemVO;

import java.util.List;

/**
 * 劳务统计服务接口（工资发放趋势 + 劳务成本占比）
 */
public interface LaborStatisticsService {

    /**
     * 工资发放趋势（按月聚合已审批/已结算工资单）
     *
     * @param projectId 项目ID
     * @param months    返回的月份数（默认 12，上限 36）
     * @return 趋势列表（按月份升序）
     */
    List<PayrollTrendItemVO> getPayrollTrend(Long projectId, Integer months);

    /**
     * 劳务成本占比（结算总额对比生效劳务合同金额）
     *
     * @param projectId 项目ID
     * @return 成本占比
     */
    LaborCostRatioVO getCostRatio(Long projectId);
}
