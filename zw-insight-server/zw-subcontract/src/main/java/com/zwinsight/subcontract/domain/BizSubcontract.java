package com.zwinsight.subcontract.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 分包合同
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_subcontract")
public class BizSubcontract extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 合同编号 */
    private String contractCode;

    /** 合同名称 */
    private String contractName;

    /** 分包方名称 */
    private String subcontractor;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 签订日期 */
    private LocalDate signingDate;

    /** 分包内容 */
    private String content;

    /** 关联预算ID */
    private Long budgetId;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 付款条款 */
    private String paymentTerms;

    /** 累计产值 */
    private BigDecimal cumulativeOutput;

    /** 累计结算金额 */
    private BigDecimal cumulativeSettlement;

    /** 累计付款金额 */
    private BigDecimal cumulativePaid;

    /** 状态（DRAFT-草稿/EFFECTIVE-生效） */
    private String status;

    /** 项目名称（非表字段，分页时按 projectId 批量回填） */
    @TableField(exist = false)
    private String projectName;
}
