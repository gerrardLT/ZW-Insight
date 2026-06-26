package com.zwinsight.material.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退货出库事件
 * <p>
 * 当退货出库单创建且关联了采购合同时发布此事件，
 * 由 MaterialReturnRefundEventListener 监听并自动创建退款申请。
 * </p>
 */
@Getter
public class MaterialReturnCreatedEvent extends ApplicationEvent {

    /** 出库单ID */
    private final Long outboundId;

    /** 关联采购合同ID */
    private final Long contractId;

    /** 项目ID */
    private final Long projectId;

    /** 出库明细列表 */
    private final List<OutboundDetailItem> details;

    public MaterialReturnCreatedEvent(Object source, Long outboundId, Long contractId,
                                      Long projectId, List<OutboundDetailItem> details) {
        super(source);
        this.outboundId = outboundId;
        this.contractId = contractId;
        this.projectId = projectId;
        this.details = details;
    }

    /**
     * 出库明细项（用于事件传递）
     */
    @Getter
    public static class OutboundDetailItem {

        /** 材料名称 */
        private final String materialName;

        /** 规格 */
        private final String specification;

        /** 单位 */
        private final String unit;

        /** 退货数量 */
        private final BigDecimal quantity;

        /** 入库单价（用于计算退款金额） */
        private final BigDecimal inboundUnitPrice;

        public OutboundDetailItem(String materialName, String specification, String unit,
                                  BigDecimal quantity, BigDecimal inboundUnitPrice) {
            this.materialName = materialName;
            this.specification = specification;
            this.unit = unit;
            this.quantity = quantity;
            this.inboundUnitPrice = inboundUnitPrice;
        }
    }
}
