package com.zwinsight.subcontract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 分包结算明细行 DTO
 */
@Data
public class SubcontractSettlementDetailDTO {

    /** 工程项名称 */
    @NotBlank(message = "工程项名称不能为空")
    private String itemName;

    /** 计量单位 */
    private String unit;

    /** 本次结算数量 */
    @NotNull(message = "结算数量不能为空")
    @Positive(message = "结算数量必须大于0")
    private BigDecimal quantity;

    /** 单价 */
    @NotNull(message = "单价不能为空")
    @Positive(message = "单价必须大于0")
    private BigDecimal unitPrice;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortOrder;
}
