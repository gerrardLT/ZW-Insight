package com.zwinsight.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.budget.domain.BizBudgetDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface BizBudgetDetailMapper extends BaseMapper<BizBudgetDetail> {

    /**
     * 累加预算明细的预算合计金额
     *
     * @param id     预算明细ID
     * @param amount 累加金额（正值追加，负值调减）
     * @return 影响行数
     */
    @Update("UPDATE biz_budget_detail SET budget_total_price = budget_total_price + #{amount} WHERE id = #{id} AND deleted = 0")
    int addBudgetTotalPrice(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 汇总指定预算下所有明细的预算合计金额
     *
     * @param budgetId 预算ID
     * @return 预算合计金额之和
     */
    @Select("SELECT COALESCE(SUM(budget_total_price), 0) FROM biz_budget_detail WHERE budget_id = #{budgetId} AND deleted = 0")
    BigDecimal sumBudgetTotalPriceByBudgetId(@Param("budgetId") Long budgetId);

    /**
     * 查询项目某科目的预算总额
     * 关联 biz_budget 表确保项目匹配且未删除
     *
     * @param projectId    项目ID
     * @param costCategory 成本科目（如 MATERIAL/LABOR/MACHINE/SUBCONTRACT）
     * @return 该科目预算总额
     */
    @Select("SELECT COALESCE(SUM(budget_total_price), 0) FROM biz_budget_detail " +
            "WHERE budget_id IN (SELECT id FROM biz_budget WHERE project_id = #{projectId} AND deleted = 0) " +
            "AND cost_category = #{costCategory} AND deleted = 0")
    BigDecimal sumBudgetByProjectAndCategory(@Param("projectId") Long projectId,
                                             @Param("costCategory") String costCategory);
}
