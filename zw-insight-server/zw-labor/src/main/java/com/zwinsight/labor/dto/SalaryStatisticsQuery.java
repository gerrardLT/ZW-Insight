package com.zwinsight.labor.dto;

import lombok.Data;

/**
 * 薪资统计查询 DTO
 */
@Data
public class SalaryStatisticsQuery {

    /** 项目ID（必填） */
    private Long projectId;

    /** 统计月份（格式：YYYY-MM，必填） */
    private String month;

    /** 班组ID（查询班组明细时必填） */
    private Long teamId;

    /** 用工类型（FIXED-自有劳务/TEMPORARY-零星用工，可选筛选） */
    private String orderType;

    /** 页码 */
    private Integer page;

    /** 每页大小 */
    private Integer size;
}
