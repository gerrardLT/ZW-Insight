package com.zwinsight.finance.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 开票申请实体
 */
@Data
public class BizInvoiceApply {

    private Long id;

    @NotNull(message = "所属项目不能为空")
    private Long projectId;

    @NotNull(message = "开票金额不能为空")
    private BigDecimal amount;

    @NotBlank(message = "发票类型不能为空")
    private String invoiceType;

    @NotBlank(message = "购方名称不能为空")
    private String buyerName;

    private String buyerTaxNo;

    private String content;

    private LocalDate applyDate;

    private String remark;

    private Integer status;

    private Long createBy;

    private LocalDate createTime;
}
