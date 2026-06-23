package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 材料出库明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_outbound_detail")
public class BizMaterialOutboundDetail extends BaseEntity {

    /** 出库单ID */
    private Long outboundId;

    /** 材料名称 */
    private String materialName;

    /** 规格 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;
}
