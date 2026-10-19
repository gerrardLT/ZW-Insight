package com.zwinsight.finance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 项目一本账只读聚合 Mapper（V2026_117，spec finance-clarity-terminology-ui 阶段三）。
 * <p>
 * <b>只读</b>：全部为聚合 SELECT，不落库不写快照（R7「累计值必须有单据支撑」约束下的
 * 安全形态——所有数字可溯源到各单据表的既有累计列或台账行）。</p>
 * <p>
 * <b>数据权限说明</b>：本 Mapper 不加 {@code @DataPermission}——多条跨表 JOIN/UNION 下
 * 注入 {@code created_by}/dept 条件会产生错误口径（应收/应付台账行并非创建人个人数据）；
 * 访问控制收敛在 Controller 层 {@code project:view} + 项目详情入口（与驾驶舱聚合接口同策略，
 * 见 {@code com.zwinsight.dashboard} 的 CockpitController 权限说明）。</p>
 */
@Mapper
public interface ProjectOnebookMapper {

    /** 收入线：生效/已结算施工合同的六段累计合计（合同额/签证/产值/结算/开票/回款） */
    @Select("SELECT COALESCE(SUM(contract_amount), 0) AS contractAmount, "
            + "COALESCE(SUM(cumulative_change_amount), 0) AS visaAmount, "
            + "COALESCE(SUM(cumulative_output), 0) AS outputAmount, "
            + "COALESCE(SUM(cumulative_settlement), 0) AS settlementAmount, "
            + "COALESCE(SUM(cumulative_invoice_amount), 0) AS invoicedAmount, "
            + "COALESCE(SUM(cumulative_received_amount), 0) AS receivedAmount "
            + "FROM biz_construction_contract "
            + "WHERE project_id = #{projectId} AND deleted = 0 AND status IN ('EFFECTIVE', 'SETTLED')")
    Map<String, Object> sumIncomeChain(@Param("projectId") Long projectId);

    /** 支出线：五类支出合同分类别聚合（合同额/累计结算/已付/已收票） */
    @Select("SELECT category, SUM(contract_amount) AS contractAmount, SUM(settled) AS settled, "
            + "SUM(paid) AS paid, SUM(invoice_received) AS invoiceReceived FROM ( "
            + "SELECT 'PURCHASE' AS category, contract_amount, cumulative_settlement AS settled, "
            + "  cumulative_paid AS paid, IFNULL(cumulative_invoice_received, 0) AS invoice_received "
            + "  FROM biz_purchase_contract WHERE project_id = #{projectId} AND deleted = 0 "
            + "UNION ALL "
            + "SELECT 'LABOR', contract_amount, cumulative_settlement, cumulative_paid, "
            + "  IFNULL(cumulative_invoice_received, 0) FROM biz_labor_contract "
            + "  WHERE project_id = #{projectId} AND deleted = 0 "
            + "UNION ALL "
            + "SELECT 'MACHINE', contract_amount, cumulative_settlement, cumulative_paid, "
            + "  IFNULL(cumulative_invoice_received, 0) FROM biz_machine_contract "
            + "  WHERE project_id = #{projectId} AND deleted = 0 "
            + "UNION ALL "
            + "SELECT 'SUBCONTRACT', contract_amount, cumulative_settlement, cumulative_paid, "
            + "  IFNULL(cumulative_invoice_received, 0) FROM biz_subcontract "
            + "  WHERE project_id = #{projectId} AND deleted = 0 "
            + "UNION ALL "
            + "SELECT 'OTHER', contract_amount, cumulative_settlement, cumulative_paid, "
            + "  IFNULL(cumulative_invoice, 0) FROM biz_other_contract "
            + "  WHERE project_id = #{projectId} AND deleted = 0 "
            + ") t GROUP BY category")
    List<Map<String, Object>> sumExpenseByCategory(@Param("projectId") Long projectId);

    /** 支出线：审批中付款申请合计（在途占用，全租户口径——额度是合同级事实） */
    @Select("SELECT COALESCE(SUM(payment_amount), 0) FROM biz_payment_apply "
            + "WHERE project_id = #{projectId} AND status = 'SUBMITTED' AND deleted = 0")
    BigDecimal sumApplying(@Param("projectId") Long projectId);

    /** 现金流入（银行口径）：勾稽到本项目回款登记的流水实收合计 */
    @Select("SELECT COALESCE(SUM(COALESCE(f.match_amount, f.amount)), 0) FROM biz_bank_flow f "
            + "JOIN biz_payment_received r ON f.matched_id = r.id "
            + "WHERE f.deleted = 0 AND f.reconciled = 1 AND f.matched_type = 'PAYMENT_RECEIVED' "
            + "AND r.deleted = 0 AND r.project_id = #{projectId}")
    BigDecimal sumCashIn(@Param("projectId") Long projectId);

    /** 现金流出（银行口径）：勾稽到本项目已批付款的流水实付合计 */
    @Select("SELECT COALESCE(SUM(COALESCE(f.match_amount, f.amount)), 0) FROM biz_bank_flow f "
            + "JOIN biz_payment_apply p ON f.matched_id = p.id "
            + "WHERE f.deleted = 0 AND f.reconciled = 1 AND f.matched_type = 'PAYMENT_APPLY' "
            + "AND p.deleted = 0 AND p.project_id = #{projectId}")
    BigDecimal sumCashOut(@Param("projectId") Long projectId);

    /** 成本线：CBS 三算合计（目标=当前预算含变更 / 已承诺 / 实际）+ 完工预测 */
    @Select("SELECT COALESCE(SUM(baseline_amount), 0) AS baseline, "
            + "COALESCE(SUM(current_amount), 0) AS currentBudget, "
            + "COALESCE(SUM(commitment_amount), 0) AS commitment, "
            + "COALESCE(SUM(actual_amount), 0) AS actual, "
            + "COALESCE(SUM(forecast_amount), 0) AS forecast "
            + "FROM biz_cost_account WHERE project_id = #{projectId} AND deleted = 0")
    Map<String, Object> sumCost(@Param("projectId") Long projectId);

    /** 应收 OPEN 余额（与应收台账同口径） */
    @Select("SELECT COALESCE(SUM(receivable_amount - written_off_amount), 0) FROM biz_receivable "
            + "WHERE project_id = #{projectId} AND status = 'OPEN' AND deleted = 0")
    BigDecimal sumReceivableOpen(@Param("projectId") Long projectId);

    /** 应付 OPEN 余额（与应付台账同口径，V2026_115） */
    @Select("SELECT COALESCE(SUM(payable_amount - paid_amount), 0) FROM biz_payable "
            + "WHERE project_id = #{projectId} AND status = 'OPEN' AND deleted = 0")
    BigDecimal sumPayableOpen(@Param("projectId") Long projectId);

    /** 税务线：销项/进项税额（台账合计；销项含红冲方向行的负向税额如实计入） */
    @Select("SELECT "
            + "(SELECT COALESCE(SUM(tax_amount), 0) FROM biz_invoice_issued "
            + "  WHERE project_id = #{projectId} AND deleted = 0) AS saleTax, "
            + "(SELECT COALESCE(SUM(tax_amount), 0) FROM biz_invoice_received "
            + "  WHERE project_id = #{projectId} AND deleted = 0) AS inputTax")
    Map<String, Object> sumTax(@Param("projectId") Long projectId);

    /** 资金占用：质保金未退（retention − returned，非 RETURNED 态） */
    @Select("SELECT COALESCE(SUM(retention_amount - IFNULL(returned_amount, 0)), 0) FROM biz_retention_money "
            + "WHERE project_id = #{projectId} AND deleted = 0 AND status != 'RETURNED'")
    BigDecimal sumRetentionOutstanding(@Param("projectId") Long projectId);

    /** 资金占用：保证金占压（已缴存/退还申请中；表无 status 列，仅以 refund_status 判定——审阅 BLOCK 修正） */
    @Select("SELECT COALESCE(SUM(amount - IFNULL(refund_amount, 0)), 0) FROM biz_security_bond "
            + "WHERE project_id = #{projectId} AND deleted = 0 "
            + "AND refund_status IN ('DEPOSITED', 'REFUND_APPLY')")
    BigDecimal sumBondOccupied(@Param("projectId") Long projectId);

    /** 资金占用：持有中票据（应收+应付票面合计） */
    @Select("SELECT COALESCE(SUM(face_amount), 0) FROM biz_bill "
            + "WHERE project_id = #{projectId} AND deleted = 0 AND status = 'HELD'")
    BigDecimal sumBillHeld(@Param("projectId") Long projectId);

    /** 备用金未还（已批申请 − 已归还 − 已冲抵） */
    @Select("SELECT COALESCE(SUM(apply_amount - IFNULL(returned_amount, 0) - IFNULL(offset_amount, 0)), 0) "
            + "FROM biz_reserve_fund_apply WHERE project_id = #{projectId} AND deleted = 0 AND status = 'APPROVED'")
    BigDecimal sumReserveOutstanding(@Param("projectId") Long projectId);

    /** 工资未付（已批/已结算工资单的未付合计） */
    @Select("SELECT COALESCE(SUM(IFNULL(unpaid, total_settlement - IFNULL(total_paid, 0))), 0) "
            + "FROM biz_labor_payroll WHERE project_id = #{projectId} AND deleted = 0 "
            + "AND status IN ('APPROVED', 'SETTLED')")
    BigDecimal sumWageUnpaid(@Param("projectId") Long projectId);

    /** 报销已批合计（项目报销） */
    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM biz_project_reimbursement "
            + "WHERE project_id = #{projectId} AND deleted = 0 AND status = 'APPROVED'")
    BigDecimal sumReimbursementApproved(@Param("projectId") Long projectId);
}
