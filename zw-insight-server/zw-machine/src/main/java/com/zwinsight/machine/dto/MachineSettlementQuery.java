package com.zwinsight.machine.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 机械结算单分页查询参数
 */
@Data
public class MachineSettlementQuery {

    /** 项目ID */
    private Long projectId;

    /** 状态 */
    private Integer status;

    /** 周期开始（筛选） */
    private LocalDate periodStart;

    /** 周期结束（筛选） */
    private LocalDate periodEnd;

    /** 页码 */
    private Integer page = 1;

    /** 每页大小 */
    private Integer size = 10;
}
