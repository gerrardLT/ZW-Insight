package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 材料入库明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_inbound_detail")
public class BizMaterialInboundDetail extends BaseEntity {

    /** 入库单ID */
    private Long inboundId;

    /** 材料名称 */
    private String materialName;

    /** 规格 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 数量 */
    private BigDecimal quantity;

    /** 金额 */
    private BigDecimal totalPrice;
}
