package com.zwinsight.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 项目进度数据
 */
@Data
public class ProgressDTO {

    /** 总计划任务数 */
    private Integer totalTasks;

    /** 已完成任务数 */
    private Integer completedTasks;

    /** 完成百分比（保留4位小数） */
    private BigDecimal completionRate;
}
