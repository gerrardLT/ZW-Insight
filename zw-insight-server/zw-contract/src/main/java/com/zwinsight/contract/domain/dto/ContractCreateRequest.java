package com.zwinsight.contract.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 施工合同创建/编辑请求 DTO
 */
@Data
public class ContractCreateRequest {

    /**
     * 项目ID
     */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /**
     * 合同类型（REGISTER-登记/CHANGE-变更/SUPPLEMENT-补充）
     */
    @NotBlank(message = "合同类型不能为空")
    private String contractType;

    /**
     * 父合同ID（变更/补充时必填）
     */
    private Long parentContractId;

    /**
     * 甲方名称
     */
    @Size(max = 200, message = "甲方名称不能超过200个字符")
    private String partyAName;

    /**
     * 甲方ID
     */
    private Long partyAId;

    /**
     * 签订日期
     */
    private LocalDate signingDate;

    /**
     * 开工日期
     */
    private LocalDate startDate;

    /**
     * 竣工日期
     */
    private LocalDate endDate;

    /**
     * 合同金额
     */
    @NotNull(message = "合同金额不能为空")
    @Positive(message = "合同金额必须大于0")
    private BigDecimal contractAmount;

    /**
     * 税率（%）
     */
    private BigDecimal taxRate;

    /**
     * 不含税金额
     */
    private BigDecimal amountWithoutTax;

    /**
     * 税额
     */
    private BigDecimal taxAmount;
}
