package com.zwinsight.dashboard.dto;

import lombok.Data;

/**
 * 项目看板聚合响应（四维度：预算、进度、合同、产值）
 */
@Data
public class ProjectDashboardDTO {

    /** 预算执行数据 */
    private BudgetExecutionDTO budget;

    /** 项目进度数据 */
    private ProgressDTO progress;

    /** 合同回款数据 */
    private ContractReceiptDTO contract;

    /** 产值上报数据 */
    private OutputTrendDTO output;
}
