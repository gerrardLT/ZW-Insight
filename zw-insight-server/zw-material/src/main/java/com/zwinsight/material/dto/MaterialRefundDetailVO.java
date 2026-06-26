package com.zwinsight.material.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 材料退款申请详情 VO（含退款明细行）
 */
@Data
public class MaterialRefundDetailVO {

    /** 退款申请ID */
    private Long id;

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

    /** 创建人 */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 退款明细列表 */
    private List<RefundDetailItem> details;

    /**
     * 退款明细行
     */
    @Data
    public static class RefundDetailItem {

        /** 明细ID */
        private Long id;

        /** 材料名称 */
        private String materialName;

        /** 规格 */
        private String specification;

        /** 单位 */
        private String unit;

        /** 退货数量 */
        private BigDecimal quantity;

        /** 入库单价 */
        private BigDecimal unitPrice;

        /** 退款金额（quantity × unitPrice） */
        private BigDecimal amount;
    }
}
