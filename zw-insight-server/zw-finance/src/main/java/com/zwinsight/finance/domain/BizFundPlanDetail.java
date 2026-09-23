package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 月度资金计划科目明细实体（V2026_58）
 * <p>
 * 月度计划的科目维度拆分（direction × category_code × amount），
 * 是"未来大额支出 TOP"与月度经营分析矩阵的数据源。
 * 应用约束：同计划同方向明细合计必须与主表 incomePlan/expensePlan 一致。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_fund_plan_detail")
public class BizFundPlanDetail extends BaseEntity {

    /** 方向：收款 */
    public static final String DIRECTION_INCOME = "INCOME";
    /** 方向：付款 */
    public static final String DIRECTION_EXPENSE = "EXPENSE";

    /** 月度资金计划ID（biz_fund_monthly_plan.id） */
    private Long planId;

    /** 方向（INCOME-收款/EXPENSE-付款） */
    private String direction;

    /** 资金科目编码（biz_fund_category.code） */
    private String categoryCode;

    /** 计划金额 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;
}
