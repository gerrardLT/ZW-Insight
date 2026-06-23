package com.zwinsight.budget.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 预算明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_budget_detail")
public class BizBudgetDetail extends BaseEntity {

    /**
     * 预算ID
     */
    private Long budgetId;

    /**
     * 费用类别（MATERIAL-材料/LABOR-人工/MACHINE-机械/SUBCONTRACT-分包/INDIRECT-间接费/OTHER-其他）
     */
    private String costCategory;

    /**
     * 费用子类
     */
    private String costSubcategory;

    /**
     * 项目名称
     */
    private String itemName;

    /**
     * 规格
     */
    private String specification;

    /**
     * 单位
     */
    private String unit;

    /**
     * 预算数量
     */
    private BigDecimal budgetQuantity;

    /**
     * 预算单价
     */
    private BigDecimal budgetUnitPrice;

    /**
     * 预算合计
     */
    private BigDecimal budgetTotalPrice;

    /**
     * 备注
     */
    private String remark;
}
