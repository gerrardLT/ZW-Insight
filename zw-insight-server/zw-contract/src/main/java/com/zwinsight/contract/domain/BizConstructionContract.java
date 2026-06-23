package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 施工合同实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_construction_contract")
public class BizConstructionContract extends BaseEntity {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 合同编号
     */
    private String contractCode;

    /**
     * 合同类型（REGISTER-登记/CHANGE-变更/SUPPLEMENT-补充）
     */
    private String contractType;

    /**
     * 父合同ID
     */
    private Long parentContractId;

    /**
     * 甲方名称
     */
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
    private BigDecimal contractAmount;

    /**
     * 税率
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

    /**
     * 累计变更金额
     */
    private BigDecimal cumulativeChangeAmount;

    /**
     * 累计产值
     */
    private BigDecimal cumulativeOutput;

    /**
     * 累计开票金额
     */
    private BigDecimal cumulativeInvoiceAmount;

    /**
     * 累计收款金额
     */
    private BigDecimal cumulativeReceivedAmount;

    /**
     * 状态（DRAFT/EFFECTIVE/SETTLED/CLOSED）
     */
    private String status;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;
}
