package com.zwinsight.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.common.datapermission.DataColumn;
import com.zwinsight.common.datapermission.DataPermission;
import com.zwinsight.project.domain.BizProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
@DataPermission(value = {
    @DataColumn(projectColumn = "id", userColumn = "created_by", deptColumn = "dept_id")
})
public interface BizProjectMapper extends BaseMapper<BizProject> {

    /**
     * 更新项目预算金额
     *
     * @param projectId    项目ID
     * @param budgetAmount 新的预算金额
     * @return 影响行数
     */
    @Update("UPDATE biz_project SET budget_amount = #{budgetAmount} WHERE id = #{projectId} AND deleted = 0")
    int updateBudgetAmount(@Param("projectId") Long projectId, @Param("budgetAmount") BigDecimal budgetAmount);

    /**
     * 更新项目状态
     *
     * @param projectId 项目ID
     * @param status    新状态
     * @return 影响行数
     */
    @Update("UPDATE biz_project SET status = #{status} WHERE id = #{projectId} AND deleted = 0")
    int updateStatus(@Param("projectId") Long projectId, @Param("status") String status);

    /**
     * 统计项目已审批的最终结算单数量（直接查表避免与 zw-finance 模块循环依赖）
     *
     * @param projectId 项目ID
     * @return 已审批结算单数量
     */
    @Select("SELECT COUNT(*) FROM biz_project_settlement " +
            "WHERE project_id = #{projectId} AND status = 'APPROVED' AND deleted = 0")
    long countApprovedSettlement(@Param("projectId") Long projectId);

    /**
     * 原子累加项目累计产值（产值上报审批通过时回写，避免并发丢失更新）
     *
     * @param projectId 项目ID
     * @param amount    本期产值
     * @return 影响行数
     */
    @Update("UPDATE biz_project SET cumulative_output = COALESCE(cumulative_output, 0) + #{amount} " +
            "WHERE id = #{projectId} AND deleted = 0")
    int addCumulativeOutput(@Param("projectId") Long projectId, @Param("amount") BigDecimal amount);

    /**
     * 原子累加项目总支出（付款申请审批通过时回写，避免并发丢失更新）
     *
     * @param projectId 项目ID
     * @param amount    本次付款金额
     * @return 影响行数
     */
    @Update("UPDATE biz_project SET total_expense = COALESCE(total_expense, 0) + #{amount} " +
            "WHERE id = #{projectId} AND deleted = 0")
    int addTotalExpense(@Param("projectId") Long projectId, @Param("amount") BigDecimal amount);

    /**
     * 原子累加项目累计合同金额（施工合同审批通过时回写，避免并发丢失更新）
     *
     * @param projectId 项目ID
     * @param amount    合同金额
     * @return 影响行数
     */
    @Update("UPDATE biz_project SET contract_amount = COALESCE(contract_amount, 0) + #{amount} " +
            "WHERE id = #{projectId} AND deleted = 0")
    int addContractAmount(@Param("projectId") Long projectId, @Param("amount") BigDecimal amount);
}
