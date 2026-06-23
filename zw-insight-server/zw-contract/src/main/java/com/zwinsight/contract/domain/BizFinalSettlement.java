package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 竣工结算实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_final_settlement")
public class BizFinalSettlement extends BaseEntity {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 结算金额
     */
    private BigDecimal settlementAmount;

    /**
     * 结算日期
     */
    private LocalDate settlementDate;

    /**
     * 状态（DRAFT-草稿/APPROVED-已审批）
     */
    private String status;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;
}
