package com.zwinsight.tender.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保证金申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_deposit_apply")
public class BizDepositApply extends BaseEntity {

    /** 投标登记ID */
    private Long registerId;

    /** 项目ID */
    private Long projectId;

    /** 保证金金额 */
    private BigDecimal depositAmount;

    /** 付款日期 */
    private LocalDate paymentDate;

    /** 状态（DRAFT/PAID） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
