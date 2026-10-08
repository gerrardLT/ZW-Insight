package com.zwinsight.budget.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 成本归集源单据查询 Mapper。
 * <p>
 * 跨模块直接 SQL 查询，避免 zw-budget 反向依赖各业务模块（沿用 {@link BudgetOccupiedMapper} 既有模式）。
 * </p>
 *
 * <h3>为什么按「单据级」而非「科目汇总级」查询</h3>
 * <p>
 * 归集必须能回答「这笔钱来自哪张单据」，否则：
 * </p>
 * <ul>
 *   <li>无法做单据级显式绑定（一个科目多个账户时的唯一正确解法）</li>
 *   <li>无法生成可追溯的流水来源单号</li>
 *   <li>无法向业务人员报告「哪些单据还没归集」</li>
 * </ul>
 *
 * <h3>成本口径约定（重要）</h3>
 * <p>
 * <b>实际成本 ≠ 付款</b>。材料以「出库消耗」计成本，分包/劳务/机械以「结算」计成本，
 * 付款只是现金流时点。把付款当成本会导致「已付款未结算」虚增成本、
 * 「已结算未付款」漏记成本，两者都让项目核算失真。
 * </p>
 */
@Mapper
public interface CostRollUpMapper {

    // ===================== 承诺额源单据：有效合同 =====================

    /** 材料采购合同（承诺） */
    @Select("SELECT id AS sourceId, contract_code AS sourceNumber, "
            + "COALESCE(contract_amount, 0) AS amount, created_at AS occurredAt "
            + "FROM biz_purchase_contract "
            + "WHERE project_id = #{projectId} AND deleted = 0 "
            + "AND status IN ('EFFECTIVE', 'REGISTER') "
            + "ORDER BY id")
    List<SourceDoc> listPurchaseContracts(@Param("projectId") Long projectId);

    /** 劳务合同（承诺） */
    @Select("SELECT id AS sourceId, contract_code AS sourceNumber, "
            + "COALESCE(contract_amount, 0) AS amount, created_at AS occurredAt "
            + "FROM biz_labor_contract "
            + "WHERE project_id = #{projectId} AND deleted = 0 "
            + "AND status IN ('EFFECTIVE', 'REGISTER') "
            + "ORDER BY id")
    List<SourceDoc> listLaborContracts(@Param("projectId") Long projectId);

    /** 机械合同（承诺） */
    @Select("SELECT id AS sourceId, contract_code AS sourceNumber, "
            + "COALESCE(contract_amount, 0) AS amount, created_at AS occurredAt "
            + "FROM biz_machine_contract "
            + "WHERE project_id = #{projectId} AND deleted = 0 "
            + "AND status IN ('EFFECTIVE', 'REGISTER') "
            + "ORDER BY id")
    List<SourceDoc> listMachineContracts(@Param("projectId") Long projectId);

    /** 分包合同（承诺） */
    @Select("SELECT id AS sourceId, contract_code AS sourceNumber, "
            + "COALESCE(contract_amount, 0) AS amount, created_at AS occurredAt "
            + "FROM biz_subcontract "
            + "WHERE project_id = #{projectId} AND deleted = 0 "
            + "AND status IN ('EFFECTIVE', 'REGISTER') "
            + "ORDER BY id")
    List<SourceDoc> listSubcontracts(@Param("projectId") Long projectId);

    // ===================== 实际成本源单据：已审批结算 / 材料消耗 =====================

    /** 采购结算（实际） */
    @Select("SELECT id AS sourceId, settlement_code AS sourceNumber, "
            + "COALESCE(settlement_amount, 0) AS amount, created_at AS occurredAt "
            + "FROM biz_purchase_settlement "
            + "WHERE project_id = #{projectId} AND deleted = 0 AND status = 'APPROVED' "
            + "ORDER BY id")
    List<SourceDoc> listPurchaseSettlements(@Param("projectId") Long projectId);

    /** 劳务结算（实际） */
    @Select("SELECT id AS sourceId, settlement_code AS sourceNumber, "
            + "COALESCE(settlement_amount, 0) AS amount, created_at AS occurredAt "
            + "FROM biz_labor_settlement "
            + "WHERE project_id = #{projectId} AND deleted = 0 AND status = 'APPROVED' "
            + "ORDER BY id")
    List<SourceDoc> listLaborSettlements(@Param("projectId") Long projectId);

    /** 分包结算（实际） */
    @Select("SELECT id AS sourceId, settlement_code AS sourceNumber, "
            + "COALESCE(settlement_amount, 0) AS amount, created_at AS occurredAt "
            + "FROM biz_subcontract_settlement "
            + "WHERE project_id = #{projectId} AND deleted = 0 AND status = 'APPROVED' "
            + "ORDER BY id")
    List<SourceDoc> listSubcontractSettlements(@Param("projectId") Long projectId);

    /** 机械工作量结算（实际；该表 status 为 TINYINT，2=已审批） */
    @Select("SELECT h.id AS sourceId, CONCAT('HISTORY:', h.id) AS sourceNumber, "
            + "h.settlement_amount AS amount, h.created_at AS occurredAt FROM biz_machine_settlement h "
            + "WHERE h.project_id = #{projectId} AND h.deleted = 0 AND h.status = 'APPROVED' "
            + "UNION ALL SELECT d.id AS sourceId, s.settlement_code AS sourceNumber, "
            + "d.subtotal AS amount, s.created_at AS occurredAt FROM biz_machine_work_settlement_detail d "
            + "JOIN biz_machine_work_settlement s ON s.id = d.settlement_id AND s.tenant_id = d.tenant_id "
            + "JOIN biz_machine_contract c ON c.id = d.contract_id AND c.tenant_id = d.tenant_id "
            + "AND c.project_id = s.project_id AND c.deleted = 0 "
            + "WHERE s.project_id = #{projectId} AND s.deleted = 0 AND s.status = 2")
    List<SourceDoc> listMachineSettlements(@Param("projectId") Long projectId);

    /**
     * 源单据投影对象。
     * <p>统一承诺/实际两类源单据的形状，归集逻辑得以复用同一套分配算法。</p>
     */
    class SourceDoc {
        private Long sourceId;
        private String sourceNumber;
        private BigDecimal amount;
        private LocalDateTime occurredAt;

        public Long getSourceId() { return sourceId; }
        public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
        public String getSourceNumber() { return sourceNumber; }
        public void setSourceNumber(String sourceNumber) { this.sourceNumber = sourceNumber; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public LocalDateTime getOccurredAt() { return occurredAt; }
        public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

        /** 金额永不为 null，简化下游累加 */
        public BigDecimal safeAmount() {
            return amount != null ? amount : BigDecimal.ZERO;
        }
    }
}
