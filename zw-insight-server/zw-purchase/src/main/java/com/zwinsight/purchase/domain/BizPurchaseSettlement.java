package com.zwinsight.purchase.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购结算实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_purchase_settlement")
public class BizPurchaseSettlement extends BaseEntity {

    /**
     * 结算单号（系统自动生成）
     */
    private String settlementNo;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 本次结算金额
     */
    private BigDecimal settlementAmount;

    /**
     * 累计结算金额
     */
    private BigDecimal cumulativeSettlement;

    /**
     * 状态（DRAFT-草稿/APPROVED-已审批）
     */
    private String status;

    /**
     * 关联入库单ID（结算依据）
     */
    private Long inboundId;

    /**
     * 关联入库单金额（结算依据，创建时按入库单自动带入）
     */
    private BigDecimal inboundAmount;

    /**
     * 结算日期
     */
    private LocalDate settlementDate;

    /**
     * 备注
     */
    private String remark;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;

    // ===== 以下为展示字段，不落库 =====

    /**
     * 关联合同名称（展示用）
     */
    @TableField(exist = false)
    private String contractName;

    /**
     * 供应商名称（展示用）
     */
    @TableField(exist = false)
    private String supplierName;

    /**
     * 关联入库单号（展示用）
     */
    @TableField(exist = false)
    private String inboundCode;
}
