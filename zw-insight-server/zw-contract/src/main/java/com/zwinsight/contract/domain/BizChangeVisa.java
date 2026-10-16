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
     * 关联的变更事件ID（可选）。单一生效源模型：有关联时事件侧不回写合同累计变更额，
     * 由本签证审批作为唯一价款确认写入；同一事件至多一张有效签证（DB 唯一 guard）。
     */
    private Long changeEventId;

    /** 签证业务日期，不用创建时间冒充。 */
    @jakarta.validation.constraints.NotNull
    private java.time.LocalDate visaDate;

    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.DecimalMax("100")
    private BigDecimal taxRate;

    public BigDecimal getTaxAmount() {
        if (changeAmount == null || taxRate == null) return null;
        return changeAmount.subtract(changeAmount.divide(BigDecimal.ONE.add(taxRate.movePointLeft(2)),
                2, java.math.RoundingMode.HALF_UP));
    }

    /**
     * 状态（DRAFT/APPROVED）
     */
    private String status;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;
}
