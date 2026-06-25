package com.zwinsight.budget.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 目标成本变更明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_budget_change_detail")
public class BizBudgetChangeDetail extends BaseEntity {

    /** 变更单ID */
    private Long changeId;

    /** 原预算明细ID */
    private Long budgetDetailId;

    /** 成本大类 */
    private String costCategory;

    /** 二级科目 */
    private String costSubcategory;

    /** 科目名称 */
    private String itemName;

    /** 原金额 */
    private BigDecimal originalAmount;

    /** 调整金额（正追加/负调减） */
    private BigDecimal adjustAmount;

    /** 调整后金额 */
    private BigDecimal adjustedAmount;

    /** 备注 */
    private String remark;
}
