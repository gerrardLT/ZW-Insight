package com.zwinsight.material.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 材料入库实体
 */
@Data
public class BizMaterialInbound {

    private Long id;

    @NotNull(message = "所属项目不能为空")
    private Long projectId;

    @NotBlank(message = "材料名称不能为空")
    private String materialName;

    private String specification;

    private String unit;

    @NotNull(message = "入库数量不能为空")
    @Min(value = 0, message = "数量不能为负")
    private BigDecimal quantity;

    @NotNull(message = "单价不能为空")
    private BigDecimal unitPrice;

    private String supplierName;

    @NotNull(message = "入库日期不能为空")
    private LocalDate inboundDate;

    private String warehouse_location;

    private String remark;

    private Integer status;

    private Long createBy;
}
