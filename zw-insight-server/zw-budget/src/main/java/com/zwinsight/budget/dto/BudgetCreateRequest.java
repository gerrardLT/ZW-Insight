package com.zwinsight.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算编制创建/编辑请求 DTO
 */
@Data
public class BudgetCreateRequest {

    /**
     * 项目ID
     */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /**
     * 预算类型（ORIGINAL-编制/CHANGE-变更）
     */
    @NotBlank(message = "预算类型不能为空")
    private String budgetType;

    /**
     * 预算总额
     */
    private BigDecimal totalAmount;
}
