package com.zwinsight.budget.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 费用子类实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_cost_subcategory")
public class BizCostSubcategory extends BaseEntity {

    /**
     * 费用类别（MATERIAL/LABOR/MACHINE/SUBCONTRACT/INDIRECT/OTHER）
     */
    private String costCategory;

    /**
     * 子类名称
     */
    private String subcategoryName;

    /**
     * 排序号
     */
    private Integer sortOrder;
}
