package com.zwinsight.budget.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizCostAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * CBS 成本账户 Mapper。
 */
@Mapper
public interface BizCostAccountMapper extends BaseMapper<BizCostAccount> {

    /**
     * 查询已建立 CBS 账户的项目 ID 列表（归集定时任务逐项目执行用）。
     * <p>
     * 只返回真正建了账户的项目：没建 CBS 的项目跑归集毫无意义，
     * 提前过滤可避免定时任务在大量新项目上空转。
     * </p>
     */
    @Select("SELECT DISTINCT project_id FROM biz_cost_account WHERE deleted = 0 ORDER BY project_id")
    List<Long> selectProjectIdsWithAccounts();

    /**
     * 分页查询成本账户（支持按父账户/WBS/类别/状态筛选）。
     *
     * @param page         分页参数
     * @param projectId    项目ID（必填）
     * @param parentId     父账户ID；为 null 时不限制（查全量而非仅根节点，
     *                     因为列表页通常展示扁平全量再前端分组）
     * @param wbsNodeId    WBS 节点ID（可空）
     * @param costCategory 费用类别（可空）
     * @param status       状态（可空）
     * @param keyword      编码/名称模糊匹配（可空）
     */
    default IPage<BizCostAccount> selectAccountPage(Page<BizCostAccount> page,
                                                    Long projectId,
                                                    Long parentId,
                                                    Long wbsNodeId,
                                                    String costCategory,
                                                    String status,
                                                    String keyword) {
        LambdaQueryWrapper<BizCostAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCostAccount::getProjectId, projectId)
                .eq(parentId != null, BizCostAccount::getParentId, parentId)
                .eq(wbsNodeId != null, BizCostAccount::getWbsNodeId, wbsNodeId)
                .eq(costCategory != null && !costCategory.isBlank(), BizCostAccount::getCostCategory, costCategory)
                .eq(status != null && !status.isBlank(), BizCostAccount::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(BizCostAccount::getAccountCode, keyword)
                        .or().like(BizCostAccount::getAccountName, keyword))
                .orderByAsc(BizCostAccount::getAccountCode);
        return selectPage(page, wrapper);
    }

    /**
     * 查询指定项目的全部成本账户（用于内存建树，避免逐层查询 N+1）。
     */
    default List<BizCostAccount> selectByProject(Long projectId) {
        return selectList(new LambdaQueryWrapper<BizCostAccount>()
                .eq(BizCostAccount::getProjectId, projectId)
                .orderByAsc(BizCostAccount::getAccountCode));
    }

    /**
     * 原子调整「当前预算」金额（用于变更事件审批通过后的传导）。
     * <p>
     * 直接走 SQL 自增而非「读-改-写」，避免并发变更互相覆盖；
     * 同时校验调整后不为负（负预算无业务意义）。
     * </p>
     *
     * @param id    账户ID
     * @param delta 调整额（正=增加，负=减少）
     * @return 影响行数（0 表示余额不足被拒绝或账户不存在）
     */
    @Update("UPDATE biz_cost_account SET current_amount = current_amount + #{delta}, "
            + "version = version + 1, updated_at = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND current_amount + #{delta} >= 0")
    int adjustCurrentAmount(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * 原子调整「已承诺」金额（合同/采购签订时占用，结算转实际时释放）。
     *
     * @param id    账户ID
     * @param delta 调整额（正=新增承诺，负=释放承诺）
     * @return 影响行数（0 表示会变成负数被拒绝或账户不存在）
     */
    @Update("UPDATE biz_cost_account SET commitment_amount = commitment_amount + #{delta}, "
            + "version = version + 1, updated_at = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND commitment_amount + #{delta} >= 0")
    int adjustCommitmentAmount(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * 原子调整「实际成本」（结算/发票/付款发生时累加）。
     *
     * @param id    账户ID
     * @param delta 调整额（正=新增实际成本，负=冲销）
     * @return 影响行数
     */
    @Update("UPDATE biz_cost_account SET actual_amount = actual_amount + #{delta}, "
            + "version = version + 1, updated_at = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND actual_amount + #{delta} >= 0")
    int adjustActualAmount(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * 按费用类别汇总项目成本（一次 SQL 出六维指标，避免应用层遍历累加）。
     *
     * @param projectId 项目ID
     * @return 每行一个费用类别的汇总
     */
    @Select("SELECT cost_category AS costCategory, "
            + "COUNT(*) AS accountCount, "
            + "COALESCE(SUM(baseline_amount), 0) AS baselineAmount, "
            + "COALESCE(SUM(current_amount), 0) AS currentAmount, "
            + "COALESCE(SUM(commitment_amount), 0) AS commitmentAmount, "
            + "COALESCE(SUM(actual_amount), 0) AS actualAmount, "
            + "COALESCE(SUM(forecast_amount), 0) AS forecastAmount "
            + "FROM biz_cost_account "
            + "WHERE project_id = #{projectId} AND deleted = 0 "
            + "GROUP BY cost_category "
            + "ORDER BY cost_category ASC")
    List<CostAccountCategorySum> sumByCategory(@Param("projectId") Long projectId);

    /**
     * 费用类别汇总行（投影对象）。
     */
    class CostAccountCategorySum {
        private String costCategory;
        private Integer accountCount;
        private BigDecimal baselineAmount;
        private BigDecimal currentAmount;
        private BigDecimal commitmentAmount;
        private BigDecimal actualAmount;
        private BigDecimal forecastAmount;

        public String getCostCategory() { return costCategory; }
        public void setCostCategory(String costCategory) { this.costCategory = costCategory; }
        public Integer getAccountCount() { return accountCount; }
        public void setAccountCount(Integer accountCount) { this.accountCount = accountCount; }
        public BigDecimal getBaselineAmount() { return baselineAmount; }
        public void setBaselineAmount(BigDecimal baselineAmount) { this.baselineAmount = baselineAmount; }
        public BigDecimal getCurrentAmount() { return currentAmount; }
        public void setCurrentAmount(BigDecimal currentAmount) { this.currentAmount = currentAmount; }
        public BigDecimal getCommitmentAmount() { return commitmentAmount; }
        public void setCommitmentAmount(BigDecimal commitmentAmount) { this.commitmentAmount = commitmentAmount; }
        public BigDecimal getActualAmount() { return actualAmount; }
        public void setActualAmount(BigDecimal actualAmount) { this.actualAmount = actualAmount; }
        public BigDecimal getForecastAmount() { return forecastAmount; }
        public void setForecastAmount(BigDecimal forecastAmount) { this.forecastAmount = forecastAmount; }
    }
}
