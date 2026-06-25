package com.zwinsight.machine.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 机械结算单创建请求
 */
@Data
public class MachineSettlementCreateRequest {

    /** 项目ID */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 结算周期开始日期 */
    @NotNull(message = "周期开始日期不能为空")
    private LocalDate periodStart;

    /** 结算周期结束日期 */
    @NotNull(message = "周期结束日期不能为空")
    private LocalDate periodEnd;
}
