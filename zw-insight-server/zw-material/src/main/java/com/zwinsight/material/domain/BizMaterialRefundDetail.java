package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 材料退款明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_refund_detail")
public class BizMaterialRefundDetail extends BaseEntity {

    /** 退款申请ID */
    private Long refundId;

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

    /** 退款金额 */
    private BigDecimal amount;
}
