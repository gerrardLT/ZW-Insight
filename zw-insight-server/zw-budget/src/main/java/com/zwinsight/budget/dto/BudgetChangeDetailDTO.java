package com.zwinsight.budget.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算变更明细 DTO
 */
@Data
public class BudgetChangeDetailDTO {

    /** 原预算明细ID */
    @NotNull(message = "预算明细ID不能为空")
    private Long budgetDetailId;

    /** 成本大类 */
    private String costCategory;

    /** 二级科目 */
    private String costSubcategory;

    /** 科目名称 */
    private String itemName;

    /** 原金额 */
    @NotNull(message = "原金额不能为空")
    private BigDecimal originalAmount;

    /** 调整金额（正追加/负调减） */
    @NotNull(message = "调整金额不能为空")
    private BigDecimal adjustAmount;
}
