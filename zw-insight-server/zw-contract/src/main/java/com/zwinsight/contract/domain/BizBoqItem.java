package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工程量清单条目实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_boq_item")
public class BizBoqItem extends BaseEntity {

    /** 施工合同ID */
    private Long contractId;

    /** 父级条目ID（0为顶层） */
    private Long parentId;

    /** 项目编码（如1.1.2） */
    private String itemCode;

    /** 项目名称 */
    private String itemName;

    /** 计量单位 */
    private String unit;

    /** 工程数量 */
    private BigDecimal quantity;

    /** 综合单价 */
    private BigDecimal unitPrice;

    /** 合价 */
    private BigDecimal totalPrice;

    /** 已完成工程量 */
    private BigDecimal completedQuantity;

    /** 层级（1-4） */
    private Integer level;

    /** 排序号 */
    private Integer sortOrder;
}
