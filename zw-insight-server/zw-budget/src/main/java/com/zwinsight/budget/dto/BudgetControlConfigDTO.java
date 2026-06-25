package com.zwinsight.budget.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 预算控制配置请求 DTO
 */
@Data
public class BudgetControlConfigDTO {

    /** 项目ID（可空表示系统默认） */
    private Long projectId;

    /** 控制模式(WARN_ONLY/BLOCK/EXEMPT) */
    @NotBlank(message = "控制模式不能为空")
    private String controlMode;

    /** 预警阈值(50-99，百分比整数) */
    @NotNull(message = "预警阈值不能为空")
    @Min(value = 50, message = "预警阈值最小为50")
    @Max(value = 99, message = "预警阈值最大为99")
    private Integer warningThreshold;
}
