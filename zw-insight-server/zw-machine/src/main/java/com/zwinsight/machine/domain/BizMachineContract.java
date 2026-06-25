package com.zwinsight.machine.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 机械合同
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_machine_contract")
public class BizMachineContract extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 合同编号 */
    private String contractCode;

    /** 合同名称 */
    private String contractName;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 设备名称 */
    private String machineName;

    /** 租赁方式（月租/台班/包月） */
    private String rentalType;

    /** 签订日期 */
    private LocalDate signingDate;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 关联预算ID */
    private Long budgetId;

    /** 合同金额 */
    private BigDecimal contractAmount;

    /** 付款条款 */
    private String paymentTerms;

    /** 累计结算金额 */
    private BigDecimal cumulativeSettlement;

    /** 累计付款金额 */
    private BigDecimal cumulativePaid;

    /** 状态（DRAFT-草稿/EFFECTIVE-生效） */
    private String status;
}
