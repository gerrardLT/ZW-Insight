package com.zwinsight.subcontract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 分包结算
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_subcontract_settlement")
public class BizSubcontractSettlement extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 合同ID */
    private Long contractId;

    /** 本次结算金额 */
    private BigDecimal settlementAmount;

    /** 累计结算金额 */
    private BigDecimal cumulativeSettlement;

    /** 状态（DRAFT-草稿/APPROVED-已审批） */
    private String status;
}
