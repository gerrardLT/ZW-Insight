package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 材料退款申请
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_refund")
public class BizMaterialRefund extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 关联出库单ID */
    private Long outboundId;

    /** 关联采购合同ID */
    private Long contractId;

    /** 退款单号 */
    private String refundCode;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 退款原因 */
    private String refundReason;

    /** 状态（DRAFT/PENDING/APPROVED/REJECTED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
