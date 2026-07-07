package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 项目材料库存
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_project_material_stock")
public class BizProjectMaterialStock extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 材料ID */
    private Long materialId;

    /** 材料名称 */
    private String materialName;

    /** 规格 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 库存数量 */
    private BigDecimal stockQuantity;

    /** 加权平均单价 */
    private BigDecimal avgUnitPrice;

    /** 累计入库数量 */
    private BigDecimal totalInbound;

    /** 累计出库数量 */
    private BigDecimal totalOutbound;

    /** 累计退货数量 */
    private BigDecimal totalReturn;

    /** 累计调入数量 */
    private BigDecimal totalTransferIn;

    /** 累计调出数量 */
    private BigDecimal totalTransferOut;
}
