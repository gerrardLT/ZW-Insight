package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 材料调拨明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_transfer_detail")
public class BizMaterialTransferDetail extends BaseEntity {

    /** 调拨单ID */
    private Long transferId;

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
