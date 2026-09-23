package com.zwinsight.finance.mapper;

import com.zwinsight.finance.dto.ContractPayableInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 合同可付数据跨模块读写 Mapper
 * <p>
 * 付款申请（biz_payment_apply）需支持对采购/劳务/机械/分包四类模块合同付款，
 * 但这些合同分属各自模块的表。为避免 finance → 各模块的循环依赖，
 * 参照 {@link SettlementDataMapper} 的做法，直接以原始 SQL 按合同类型读取
 * 累计结算/累计已付，并在审批通过时原子累加累计已付。
 * </p>
 * <p>
 * 说明：不做数据权限过滤（付款审批回调为系统内部原子回写），与 SettlementDataMapper 口径一致。
 * 每个合同类型显式一组方法（不使用动态表名拼接），杜绝 SQL 注入风险。
 * </p>
 */
@Mapper
public interface ContractPayableMapper {

    // ==================== 采购合同 biz_purchase_contract ====================
    @Select("SELECT cumulative_settlement AS cumulativeSettlement, cumulative_paid AS cumulativePaid "
            + "FROM biz_purchase_contract WHERE id = #{id} AND deleted = 0")
    ContractPayableInfo purchasePayable(@Param("id") Long id);

    @Update("UPDATE biz_purchase_contract SET cumulative_paid = COALESCE(cumulative_paid, 0) + #{amount} "
            + "WHERE id = #{id} AND deleted = 0")
    int addPurchasePaid(@Param("id") Long id, @Param("amount") BigDecimal amount);

    // ==================== 劳务合同 biz_labor_contract ====================
    @Select("SELECT cumulative_settlement AS cumulativeSettlement, cumulative_paid AS cumulativePaid "
            + "FROM biz_labor_contract WHERE id = #{id} AND deleted = 0")
    ContractPayableInfo laborPayable(@Param("id") Long id);

    @Update("UPDATE biz_labor_contract SET cumulative_paid = COALESCE(cumulative_paid, 0) + #{amount} "
            + "WHERE id = #{id} AND deleted = 0")
    int addLaborPaid(@Param("id") Long id, @Param("amount") BigDecimal amount);

    // ==================== 机械合同 biz_machine_contract ====================
    @Select("SELECT cumulative_settlement AS cumulativeSettlement, cumulative_paid AS cumulativePaid "
            + "FROM biz_machine_contract WHERE id = #{id} AND deleted = 0")
    ContractPayableInfo machinePayable(@Param("id") Long id);

    @Update("UPDATE biz_machine_contract SET cumulative_paid = COALESCE(cumulative_paid, 0) + #{amount} "
            + "WHERE id = #{id} AND deleted = 0")
    int addMachinePaid(@Param("id") Long id, @Param("amount") BigDecimal amount);

    // ==================== 分包合同 biz_subcontract ====================
    @Select("SELECT cumulative_settlement AS cumulativeSettlement, cumulative_paid AS cumulativePaid "
            + "FROM biz_subcontract WHERE id = #{id} AND deleted = 0")
    ContractPayableInfo subcontractPayable(@Param("id") Long id);

    @Update("UPDATE biz_subcontract SET cumulative_paid = COALESCE(cumulative_paid, 0) + #{amount} "
            + "WHERE id = #{id} AND deleted = 0")
    int addSubcontractPaid(@Param("id") Long id, @Param("amount") BigDecimal amount);

    // ==================== 应付未付聚合（驾驶舱 §9.1/§12，资金流转 §3.1）====================

    /**
     * 应付未付合计 = Σ（已确认应付 − 已付）= Σ(cumulative_settlement − cumulative_paid)。
     * <p><b>为何是这 5 张表（实证结论，勿随意增减）</b>：它们正是
     * {@code PaymentApplyService.resolvePayable/addCumulativePaid} 的付款回写路由目标。
     * 2026-09-24 线上对账验证：16 笔付款申请按 contract_category 分布
     * （PURCHASE 2450万 / LABOR 1600万 / SUBCONTRACT 1100万 / MACHINE 600万 / OTHER_EXPENSE 100万）
     * 与五表 cumulative_paid 逐一对应。</p>
     * <p><b>为何排除 biz_expense_contract</b>：该表虽名为“通用支出合同”且含
     * cumulative_settlement/cumulative_paid 列，但付款链路**不回写它**（仅用于合同到期扫描
     * ContractExpiryService 与档案展示 ArchiveService）。历史教训：2026-08-13 批次二 L4
     * stage_9K 500 事故正是因为退款扣减查了该表而付款回写在 biz_purchase_contract，
     * 扣减长期命中 0 行静默失效。若将其纳入聚合会虚增应付（实测多算 200 万）。</p>
     *
     * @param projectId 项目ID（空=公司整体）
     * @return 应付未付合计（无合同时 0）
     */
    @Select("<script>"
            + "SELECT IFNULL(SUM(t.payable), 0) FROM ("
            + "  SELECT COALESCE(cumulative_settlement,0) - COALESCE(cumulative_paid,0) AS payable"
            + "    FROM biz_purchase_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL"
            + "  SELECT COALESCE(cumulative_settlement,0) - COALESCE(cumulative_paid,0)"
            + "    FROM biz_labor_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL"
            + "  SELECT COALESCE(cumulative_settlement,0) - COALESCE(cumulative_paid,0)"
            + "    FROM biz_machine_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL"
            + "  SELECT COALESCE(cumulative_settlement,0) - COALESCE(cumulative_paid,0)"
            + "    FROM biz_subcontract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL"
            + "  SELECT COALESCE(cumulative_settlement,0) - COALESCE(cumulative_paid,0)"
            + "    FROM biz_other_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + ") t"
            + "</script>")
    BigDecimal sumPayableOutstanding(@Param("projectId") Long projectId);

    /**
     * 已确认应付合计（Σ cumulative_settlement）——支付率分母（资金流转 §10.2）。
     * 表范围与口径同 {@link #sumPayableOutstanding(Long)}。
     */
    @Select("<script>"
            + "SELECT IFNULL(SUM(t.settled), 0) FROM ("
            + "  SELECT COALESCE(cumulative_settlement,0) AS settled FROM biz_purchase_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL SELECT COALESCE(cumulative_settlement,0) FROM biz_labor_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL SELECT COALESCE(cumulative_settlement,0) FROM biz_machine_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL SELECT COALESCE(cumulative_settlement,0) FROM biz_subcontract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL SELECT COALESCE(cumulative_settlement,0) FROM biz_other_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + ") t"
            + "</script>")
    BigDecimal sumConfirmedPayable(@Param("projectId") Long projectId);

    /**
     * 已付合计（Σ cumulative_paid）——支付率分子（资金流转 §10.2）。
     * <p><b>口径声明</b>：cumulative_paid 由付款申请<b>审批通过</b>时回写
     * （{@code PaymentApplyService.onApproved}），属<b>审批口径</b>而非银行现金口径。
     * 现金口径请看 {@code BizBankFlowMapper.sumMatchedGroupByPaymentApply()}（勾稽合计），
     * 调用方必须向用户标明用的是哪个口径，不得笼统称“实际支付”。</p>
     */
    @Select("<script>"
            + "SELECT IFNULL(SUM(t.paid), 0) FROM ("
            + "  SELECT COALESCE(cumulative_paid,0) AS paid FROM biz_purchase_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL SELECT COALESCE(cumulative_paid,0) FROM biz_labor_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL SELECT COALESCE(cumulative_paid,0) FROM biz_machine_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL SELECT COALESCE(cumulative_paid,0) FROM biz_subcontract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + "  UNION ALL SELECT COALESCE(cumulative_paid,0) FROM biz_other_contract WHERE deleted = 0"
            + "    <if test='projectId != null'> AND project_id = #{projectId}</if>"
            + ") t"
            + "</script>")
    BigDecimal sumCumulativePaid(@Param("projectId") Long projectId);
}
