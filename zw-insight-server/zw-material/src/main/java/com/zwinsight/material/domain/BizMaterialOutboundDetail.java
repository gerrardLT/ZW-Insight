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

    /** 新增退货必须显式选择来源；历史空值不推断。 */
    private Long inboundDetailId;

    /** 关联BOQ工程量清单项ID（可空；用于工程量-材料消耗限额对账） */
    private Long boqItemId;

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
