package com.zwinsight.finance.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 开票申请创建/编辑请求 DTO
 */
@Data
public class InvoiceApplyCreateRequest {

    /**
     * 项目ID
     */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /**
     * 施工合同ID
     */
    @NotNull(message = "施工合同ID不能为空")
    private Long contractId;

    /**
     * 申请日期（业务日期，格式 yyyy-MM-dd，用于财务封账期间校验）
     */
    @NotNull(message = "申请日期不能为空")
    private String applyDate;

    /**
     * 发票类型（SPECIAL-专票/NORMAL-普票）
     */
    @Size(max = 20, message = "发票类型不能超过20个字符")
    private String invoiceType;

    /**
     * 本次开票金额
     */
    @NotNull(message = "开票金额不能为空")
    @Positive(message = "开票金额必须大于0")
    private BigDecimal invoiceAmount;

    /**
     * 发票抬头
     */
    @Size(max = 200, message = "发票抬头不能超过200个字符")
    private String invoiceTitle;

    /**
     * 纳税人识别号
     */
    @Size(max = 50, message = "纳税人识别号不能超过50个字符")
    private String taxpayerId;

    /**
     * 银行账号
     */
    @Size(max = 50, message = "银行账号不能超过50个字符")
    private String bankAccount;

    /**
     * 开户银行
     */
    @Size(max = 100, message = "开户银行名称不能超过100个字符")
    private String bankName;
}
