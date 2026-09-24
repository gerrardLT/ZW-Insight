package com.zwinsight.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 单据穿透链只读 Mapper（驾驶舱 P2-4，UI §13/§16）。
 * <p>
 * 成本分类 → 供应商 → 合同 → 原始单据的逐级下钻查询。五类支出合同分属五个模块的表
 * （biz_purchase_contract / biz_labor_contract / biz_machine_contract / biz_subcontract /
 * biz_other_contract），zw-dashboard 不依赖 labor/machine/subcontract 模块，
 * 故按 {@code ContractPayableMapper} 既有先例用原生 SQL 只读查询（每个方法固定单表，
 * 类别路由在 Service 层白名单完成，杜绝 {@code ${}} 拼接注入面）。
 * </p>
 * <p>tenant_id 条件由 TenantLineInnerInterceptor 自动注入（biz_ 前缀表参与租户隔离）；
 * 逻辑删除 deleted=0 需显式声明（原生 @Select 不走 @TableLogic）。</p>
 * <p>供应商列名各表不统一（实证 2026-09-24）：采购/劳务/其他合同用 party_b_name（其他合同
 * 无 party_b_id）、机械合同用 supplier_name、分包合同 supplier_name 可空回退 subcontractor，
 * 统一以 COALESCE(NULLIF(...)) 归一为 supplierName 输出。</p>
 */
@Mapper
public interface DrillDownMapper {

    // ==================== 成本分类 → 供应商聚合 ====================

    /** 采购合同按供应商聚合（PURCHASE） */
    @Select("SELECT COALESCE(NULLIF(party_b_name, ''), NULLIF(supplier_name, '')) AS supplierName, "
            + "COUNT(*) AS contractCount, "
            + "COALESCE(SUM(contract_amount), 0) AS contractAmount, "
            + "COALESCE(SUM(cumulative_settlement), 0) AS settlementTotal, "
            + "COALESCE(SUM(cumulative_paid), 0) AS paidTotal, "
            + "COALESCE(SUM(cumulative_invoice_received), 0) AS invoiceTotal "
            + "FROM biz_purchase_contract "
            + "WHERE deleted = 0 AND project_id = #{projectId} "
            + "GROUP BY supplierName")
    List<Map<String, Object>> supplierAggPurchase(@Param("projectId") Long projectId);

    /** 劳务合同按供应商（乙方）聚合（LABOR） */
    @Select("SELECT COALESCE(NULLIF(party_b_name, ''), NULLIF(team_name, '')) AS supplierName, "
            + "COUNT(*) AS contractCount, "
            + "COALESCE(SUM(contract_amount), 0) AS contractAmount, "
            + "COALESCE(SUM(cumulative_settlement), 0) AS settlementTotal, "
            + "COALESCE(SUM(cumulative_paid), 0) AS paidTotal, "
            + "0 AS invoiceTotal "
            + "FROM biz_labor_contract "
            + "WHERE deleted = 0 AND project_id = #{projectId} "
            + "GROUP BY supplierName")
    List<Map<String, Object>> supplierAggLabor(@Param("projectId") Long projectId);

    /** 机械合同按供应商聚合（MACHINE） */
    @Select("SELECT COALESCE(NULLIF(supplier_name, ''), '（未登记供应商）') AS supplierName, "
            + "COUNT(*) AS contractCount, "
            + "COALESCE(SUM(contract_amount), 0) AS contractAmount, "
            + "COALESCE(SUM(cumulative_settlement), 0) AS settlementTotal, "
            + "COALESCE(SUM(cumulative_paid), 0) AS paidTotal, "
            + "0 AS invoiceTotal "
            + "FROM biz_machine_contract "
            + "WHERE deleted = 0 AND project_id = #{projectId} "
            + "GROUP BY supplierName")
    List<Map<String, Object>> supplierAggMachine(@Param("projectId") Long projectId);

    /** 分包合同按供应商聚合（SUBCONTRACT；supplier_name 为空回退分包方名称） */
    @Select("SELECT COALESCE(NULLIF(supplier_name, ''), NULLIF(subcontractor, ''), '（未登记分包方）') AS supplierName, "
            + "COUNT(*) AS contractCount, "
            + "COALESCE(SUM(contract_amount), 0) AS contractAmount, "
            + "COALESCE(SUM(cumulative_settlement), 0) AS settlementTotal, "
            + "COALESCE(SUM(cumulative_paid), 0) AS paidTotal, "
            + "0 AS invoiceTotal "
            + "FROM biz_subcontract "
            + "WHERE deleted = 0 AND project_id = #{projectId} "
            + "GROUP BY supplierName")
    List<Map<String, Object>> supplierAggSubcontract(@Param("projectId") Long projectId);

    /** 其他支出合同按乙方聚合（OTHER_EXPENSE；无供应商ID列，仅有名称） */
    @Select("SELECT COALESCE(NULLIF(party_b_name, ''), '（未登记乙方）') AS supplierName, "
            + "COUNT(*) AS contractCount, "
            + "COALESCE(SUM(contract_amount), 0) AS contractAmount, "
            + "COALESCE(SUM(cumulative_settlement), 0) AS settlementTotal, "
            + "COALESCE(SUM(cumulative_paid), 0) AS paidTotal, "
            + "0 AS invoiceTotal "
            + "FROM biz_other_contract "
            + "WHERE deleted = 0 AND project_id = #{projectId} AND contract_category = 'OTHER_EXPENSE' "
            + "GROUP BY supplierName")
    List<Map<String, Object>> supplierAggOther(@Param("projectId") Long projectId);

    // ==================== 供应商 → 合同清单 ====================

    /** 采购合同清单（supplierName 为空=该类全部） */
    @Select("<script>"
            + "SELECT id, contract_code AS contractCode, contract_name AS contractName, "
            + "COALESCE(NULLIF(party_b_name, ''), NULLIF(supplier_name, '')) AS supplierName, "
            + "signing_date AS signingDate, contract_amount AS contractAmount, "
            + "cumulative_settlement AS cumulativeSettlement, cumulative_paid AS cumulativePaid, "
            + "cumulative_invoice_received AS cumulativeInvoiceReceived, "
            + "status, workflow_instance_id AS workflowInstanceId "
            + "FROM biz_purchase_contract "
            + "WHERE deleted = 0 AND project_id = #{projectId} "
            + "<if test='supplierName != null and supplierName != \"\"'> AND COALESCE(NULLIF(party_b_name, ''), NULLIF(supplier_name, '')) = #{supplierName}</if>"
            + " ORDER BY signing_date DESC, id DESC"
            + "</script>")
    List<Map<String, Object>> contractsPurchase(@Param("projectId") Long projectId,
                                                @Param("supplierName") String supplierName);

    /** 劳务合同清单 */
    @Select("<script>"
            + "SELECT id, contract_code AS contractCode, contract_name AS contractName, "
            + "COALESCE(NULLIF(party_b_name, ''), NULLIF(team_name, '')) AS supplierName, "
            + "signing_date AS signingDate, contract_amount AS contractAmount, "
            + "cumulative_settlement AS cumulativeSettlement, cumulative_paid AS cumulativePaid, "
            + "0 AS cumulativeInvoiceReceived, "
            + "status, workflow_instance_id AS workflowInstanceId "
            + "FROM biz_labor_contract "
            + "WHERE deleted = 0 AND project_id = #{projectId} "
            + "<if test='supplierName != null and supplierName != \"\"'> AND COALESCE(NULLIF(party_b_name, ''), NULLIF(team_name, '')) = #{supplierName}</if>"
            + " ORDER BY signing_date DESC, id DESC"
            + "</script>")
    List<Map<String, Object>> contractsLabor(@Param("projectId") Long projectId,
                                             @Param("supplierName") String supplierName);

    /** 机械合同清单（无 workflow 列，审批轨迹从付款单据进入） */
    @Select("<script>"
            + "SELECT id, contract_code AS contractCode, contract_name AS contractName, "
            + "COALESCE(NULLIF(supplier_name, ''), '（未登记供应商）') AS supplierName, "
            + "signing_date AS signingDate, contract_amount AS contractAmount, "
            + "cumulative_settlement AS cumulativeSettlement, cumulative_paid AS cumulativePaid, "
            + "0 AS cumulativeInvoiceReceived, "
            + "status, NULL AS workflowInstanceId "
            + "FROM biz_machine_contract "
            + "WHERE deleted = 0 AND project_id = #{projectId} "
            + "<if test='supplierName != null and supplierName != \"\"'> AND COALESCE(NULLIF(supplier_name, ''), '（未登记供应商）') = #{supplierName}</if>"
            + " ORDER BY signing_date DESC, id DESC"
            + "</script>")
    List<Map<String, Object>> contractsMachine(@Param("projectId") Long projectId,
                                               @Param("supplierName") String supplierName);

    /** 分包合同清单（无 workflow 列） */
    @Select("<script>"
            + "SELECT id, contract_code AS contractCode, contract_name AS contractName, "
            + "COALESCE(NULLIF(supplier_name, ''), NULLIF(subcontractor, ''), '（未登记分包方）') AS supplierName, "
            + "signing_date AS signingDate, contract_amount AS contractAmount, "
            + "cumulative_settlement AS cumulativeSettlement, cumulative_paid AS cumulativePaid, "
            + "0 AS cumulativeInvoiceReceived, "
            + "status, NULL AS workflowInstanceId "
            + "FROM biz_subcontract "
            + "WHERE deleted = 0 AND project_id = #{projectId} "
            + "<if test='supplierName != null and supplierName != \"\"'> AND COALESCE(NULLIF(supplier_name, ''), NULLIF(subcontractor, ''), '（未登记分包方）') = #{supplierName}</if>"
            + " ORDER BY signing_date DESC, id DESC"
            + "</script>")
    List<Map<String, Object>> contractsSubcontract(@Param("projectId") Long projectId,
                                                   @Param("supplierName") String supplierName);

    /** 其他支出合同清单（无 contract_code 列、无 workflow 列——如实返回 NULL） */
    @Select("<script>"
            + "SELECT id, NULL AS contractCode, contract_name AS contractName, "
            + "COALESCE(NULLIF(party_b_name, ''), '（未登记乙方）') AS supplierName, "
            + "signing_date AS signingDate, contract_amount AS contractAmount, "
            + "cumulative_settlement AS cumulativeSettlement, cumulative_paid AS cumulativePaid, "
            + "0 AS cumulativeInvoiceReceived, "
            + "status, NULL AS workflowInstanceId "
            + "FROM biz_other_contract "
            + "WHERE deleted = 0 AND project_id = #{projectId} AND contract_category = 'OTHER_EXPENSE' "
            + "<if test='supplierName != null and supplierName != \"\"'> AND COALESCE(NULLIF(party_b_name, ''), '（未登记乙方）') = #{supplierName}</if>"
            + " ORDER BY signing_date DESC, id DESC"
            + "</script>")
    List<Map<String, Object>> contractsOther(@Param("projectId") Long projectId,
                                             @Param("supplierName") String supplierName);

    // ==================== 合同 → 结算单 ====================

    /** 采购结算单（带单号/结算日期/关联入库单号——§13 采购→入库环节） */
    @Select("SELECT s.id, s.settlement_no AS docNo, s.settlement_date AS docDate, "
            + "s.settlement_amount AS amount, s.status, "
            + "s.workflow_instance_id AS workflowInstanceId, "
            + "i.inbound_code AS refInboundCode, s.inbound_amount AS refInboundAmount "
            + "FROM biz_purchase_settlement s "
            + "LEFT JOIN biz_material_inbound i ON i.id = s.inbound_id AND i.deleted = 0 "
            + "WHERE s.deleted = 0 AND s.contract_id = #{contractId} "
            + "ORDER BY s.settlement_date DESC, s.id DESC")
    List<Map<String, Object>> settlementsPurchase(@Param("contractId") Long contractId);

    /** 劳务结算单（表无单号/结算日期列，登记时间用 created_at 并如实命名） */
    @Select("SELECT id, NULL AS docNo, created_at AS docDate, "
            + "settlement_amount AS amount, status, NULL AS workflowInstanceId "
            + "FROM biz_labor_settlement "
            + "WHERE deleted = 0 AND contract_id = #{contractId} "
            + "ORDER BY id DESC")
    List<Map<String, Object>> settlementsLabor(@Param("contractId") Long contractId);

    /** 机械结算单（同上，无单号列） */
    @Select("SELECT id, NULL AS docNo, created_at AS docDate, "
            + "settlement_amount AS amount, status, NULL AS workflowInstanceId "
            + "FROM biz_machine_settlement "
            + "WHERE deleted = 0 AND contract_id = #{contractId} "
            + "ORDER BY id DESC")
    List<Map<String, Object>> settlementsMachine(@Param("contractId") Long contractId);

    /** 分包结算单（同上，无单号列） */
    @Select("SELECT id, NULL AS docNo, created_at AS docDate, "
            + "settlement_amount AS amount, status, NULL AS workflowInstanceId "
            + "FROM biz_subcontract_settlement "
            + "WHERE deleted = 0 AND contract_id = #{contractId} "
            + "ORDER BY id DESC")
    List<Map<String, Object>> settlementsSubcontract(@Param("contractId") Long contractId);

    // ==================== 成本账户 → 构成流水（INDIRECT/OTHER 等无供应商维度的类别） ====================

    /**
     * 某费用类别下全部成本账户的「实际成本」变动流水（amount_type=ACTUAL）。
     * <p>costCategory 支持多选（OTHER_EXPENSE 穿透覆盖 INDIRECT+OTHER 两类账户）。</p>
     */
    @Select("<script>"
            + "SELECT t.id, t.account_id AS accountId, a.account_code AS accountCode, "
            + "a.account_name AS accountName, a.cost_category AS costCategory, "
            + "t.delta_amount AS deltaAmount, t.balance_after AS balanceAfter, "
            + "t.source_type AS sourceType, t.source_id AS sourceId, t.source_number AS sourceNumber, "
            + "t.occurred_at AS occurredAt, t.remark "
            + "FROM biz_cost_account_txn t "
            + "JOIN biz_cost_account a ON a.id = t.account_id AND a.deleted = 0 "
            + "WHERE t.deleted = 0 AND t.project_id = #{projectId} AND t.amount_type = 'ACTUAL' "
            + "AND a.cost_category IN "
            + "<foreach collection='costCategories' item='cat' open='(' separator=',' close=')'>#{cat}</foreach>"
            + " ORDER BY t.occurred_at DESC, t.id DESC LIMIT 200"
            + "</script>")
    List<Map<String, Object>> accountActualTxn(@Param("projectId") Long projectId,
                                               @Param("costCategories") List<String> costCategories);
}
