package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 材料盘点明细（盘盈亏差异流水）
 * <p>登记阶段记录账面数量/实盘数量/差异，审批阶段据实盘数量调整库存。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_inventory_detail")
public class BizMaterialInventoryDetail extends BaseEntity {

    /** 盘点单ID */
    private Long inventoryId;

    /** 库存ID */
    private Long stockId;

    /** 材料名称（快照） */
    private String materialName;

    /** 规格（快照） */
    private String specification;

    /** 单位（快照） */
    private String unit;

    /** 账面数量（登记时库存快照） */
    private BigDecimal bookQuantity;

    /** 实盘数量 */
    private BigDecimal actualQuantity;

    /** 差异数量（实盘-账面，正数盘盈/负数盘亏） */
    private BigDecimal diffQuantity;
}
