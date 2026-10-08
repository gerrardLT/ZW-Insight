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
     * 统计项目关联的投标报名数量（直接查表避免与 zw-tender 模块循环依赖）
     *
     * @param projectId 项目ID
     * @return 关联投标报名数量
     */
    @Select("SELECT COUNT(*) FROM biz_tender_register " +
            "WHERE project_id = #{projectId} AND deleted = 0")
    long countTenderRegisters(@Param("projectId") Long projectId);

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
     * 原子累加项目竣工结算额（合同竣工结算审批通过时回写，避免读后覆盖整行丢失并发更新）
     *
     * @return 影响行数；0 表示项目不存在
     */
    @Update("UPDATE biz_project SET settlement_amount = COALESCE(settlement_amount, 0) + #{amount} " +
            "WHERE id = #{projectId} AND deleted = 0")
    int addSettlementAmount(@Param("projectId") Long projectId, @Param("amount") BigDecimal amount);

    /**
     * 原子增减项目应收账款（台账口径：biz_receivable OPEN 余额合计；V2026_57 新增）
     * <p>由结算审批生成应收（正向）与回款核销（负向）同事务双写，
     * 支持负 amount 冲减；不变量：receivable_amount ≥ 0 由调用方校验。</p>
     *
     * @param projectId 项目ID
     * @param amount    增减金额（可为负）
     * @return 影响行数
     */
    @Update("UPDATE biz_project SET receivable_amount = COALESCE(receivable_amount, 0) + #{amount} " +
            "WHERE id = #{projectId} AND deleted = 0")
    int addReceivableAmount(@Param("projectId") Long projectId, @Param("amount") BigDecimal amount);

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

    // ================= P1-M1 深度优化新增（V2026_81，蓝图 01-project.md） =================

    /**
     * 统计项目 EFFECTIVE 施工合同数（开工准入守卫 I2；跨表只读避免循环依赖，惯例同上）
     */
    @Select("SELECT COUNT(*) FROM biz_construction_contract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    long countEffectiveConstructionContracts(@Param("projectId") Long projectId);

    /**
     * 项目应付未付余额 = Σ五类支出合同（累计结算 − 累计已付） + 其他付款未付部分。
     * 结项实口径条件 I5-2（替换原产值−收入简化式）。
     */
    @Select("SELECT COALESCE(SUM(payable), 0) FROM (" +
            " SELECT COALESCE(cumulative_settlement,0) - COALESCE(cumulative_paid,0) AS payable FROM biz_purchase_contract WHERE project_id = #{projectId} AND deleted = 0" +
            " UNION ALL SELECT COALESCE(cumulative_settlement,0) - COALESCE(cumulative_paid,0) FROM biz_labor_contract WHERE project_id = #{projectId} AND deleted = 0" +
            " UNION ALL SELECT COALESCE(cumulative_settlement,0) - COALESCE(cumulative_paid,0) FROM biz_machine_contract WHERE project_id = #{projectId} AND deleted = 0" +
            " UNION ALL SELECT COALESCE(cumulative_settlement,0) - COALESCE(cumulative_paid,0) FROM biz_subcontract WHERE project_id = #{projectId} AND deleted = 0" +
            ") t")
    BigDecimal sumPayableOutstanding(@Param("projectId") Long projectId);

    /**
     * 未退质保金合计（结项条件 I5-3：RETENTION 类资金单据未到 REFUNDED/终态的余额）
     */
    @Select("SELECT COALESCE(SUM(retention_amount - COALESCE(returned_amount, 0)), 0) FROM biz_retention_money " +
            "WHERE project_id = #{projectId} AND status <> 'REFUNDED' AND deleted = 0")
    BigDecimal sumUnreturnedRetention(@Param("projectId") Long projectId);

    /**
     * 未退保证金合计（结项条件 I5-4；表=biz_security_bond：amount − refund_amount，
     * 仅统计 refund_status ≠ REFUNDED 的有效记录）
     */
    @Select("SELECT COALESCE(SUM(amount - COALESCE(refund_amount, 0)), 0) FROM biz_security_bond " +
            "WHERE project_id = #{projectId} AND refund_status <> 'REFUNDED' AND deleted = 0")
    BigDecimal sumUnreturnedSecurityBond(@Param("projectId") Long projectId);

    /**
     * 未退备用金/押金合计（结项条件 I5-4b；表=biz_reserve_fund_apply：申请 − 已退 − 已抵扣，
     * 仅统计 APPROVED 记录）
     */
    @Select("SELECT COALESCE(SUM(apply_amount - COALESCE(returned_amount, 0) - COALESCE(offset_amount, 0)), 0) FROM biz_reserve_fund_apply " +
            "WHERE project_id = #{projectId} AND status = 'APPROVED' AND deleted = 0")
    BigDecimal sumUnreturnedReserveFund(@Param("projectId") Long projectId);

    /**
     * 读取 sys_config 布尔开关（跨表只读；未配置/异常一律回退默认值，绝不阻断主流程）
     */
    @Select("SELECT config_value FROM sys_config WHERE config_key = #{key} AND deleted = 0 LIMIT 1")
    String selectConfigValue(@Param("key") String key);

    /** 在途付款申请数（终止守卫：审批中单据须清零才可终止） */
    @Select("SELECT COUNT(*) FROM biz_payment_apply WHERE project_id = #{projectId} " +
            "AND status IN ('SUBMITTED', 'PENDING_APPROVAL') AND deleted = 0")
    long countInFlightPayments(@Param("projectId") Long projectId);

    /** 在途项目结算单数（终止守卫） */
    @Select("SELECT COUNT(*) FROM biz_project_settlement WHERE project_id = #{projectId} " +
            "AND status IN ('SUBMITTED', 'PENDING_APPROVAL') AND deleted = 0")
    long countInFlightSettlements(@Param("projectId") Long projectId);
}
