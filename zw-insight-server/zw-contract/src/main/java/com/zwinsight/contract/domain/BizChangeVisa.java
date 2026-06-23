package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 变更签证实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_change_visa")
public class BizChangeVisa extends BaseEntity {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 变更类型（DESIGN_CHANGE-设计变更/SITE_VISA-现场签证/QUANTITY_CHANGE-工程量变更）
     */
    private String changeType;

    /**
     * 变更原因
     */
    private String changeReason;

    /**
     * 变更内容
     */
    private String changeContent;

    /**
     * 变更金额
     */
    private BigDecimal changeAmount;

    /**
     * 状态（DRAFT/APPROVED）
     */
    private String status;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;
}
