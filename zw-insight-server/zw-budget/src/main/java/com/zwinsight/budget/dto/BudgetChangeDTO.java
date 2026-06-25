package com.zwinsight.budget.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 预算变更请求 DTO
 */
@Data
public class BudgetChangeDTO {

    /** 项目ID */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 原预算编制ID */
    @NotNull(message = "预算ID不能为空")
    private Long budgetId;

    /** 变更原因 */
    @NotBlank(message = "变更原因不能为空")
    private String changeReason;

    /** 变更明细列表 */
    @NotEmpty(message = "变更明细不能为空")
    @Valid
    private List<BudgetChangeDetailDTO> details;
}
