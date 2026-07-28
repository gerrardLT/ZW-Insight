package com.zwinsight.budget.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 预算占用查询 Mapper
 * <p>
 * 跨模块查询各类合同的已签金额合计，用于预算调减校验和预算执行率计算。
 * 直接使用 SQL 查询避免模块间循环依赖。
 * </p>
 */
@Mapper
public interface BudgetOccupiedMapper {

    /**
     * 查询分包合同已签金额合计
     */
    @Select("SELECT COALESCE(SUM(contract_amount), 0) FROM biz_subcontract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumSubcontractAmount(@Param("projectId") Long projectId);

    /**
     * 查询劳务合同已签金额合计
     */
    @Select("SELECT COALESCE(SUM(contract_amount), 0) FROM biz_labor_contract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumLaborContractAmount(@Param("projectId") Long projectId);

    /**
     * 查询机械合同已签金额合计
     */
    @Select("SELECT COALESCE(SUM(contract_amount), 0) FROM biz_machine_contract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumMachineContractAmount(@Param("projectId") Long projectId);

    /**
     * 查询采购合同（材料）已签金额合计
     */
    @Select("SELECT COALESCE(SUM(contract_amount), 0) FROM biz_purchase_contract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumPurchaseContractAmount(@Param("projectId") Long projectId);

    // ===================== 按科目查询已发生额（用于预算执行率计算）=====================

    /**
     * 按科目查询已签合同金额合计 — 材料（采购合同）
     */
    @Select("SELECT COALESCE(SUM(contract_amount), 0) FROM biz_purchase_contract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumContractAmountForMaterial(@Param("projectId") Long projectId);

    /**
     * 查询已审批材料调拨对项目预算占用的净影响（调入为正、调出为负，按明细 quantity×unit_price）
     * <p>调入项目增加材料占用、调出项目减少材料占用，用于预算执行率计算。</p>
     */
    @Select("SELECT COALESCE(SUM(net), 0) FROM (" +
            "SELECT COALESCE(SUM(d.quantity * d.unit_price), 0) AS net FROM biz_material_transfer t " +
            "JOIN biz_material_transfer_detail d ON d.transfer_id = t.id AND d.deleted = 0 " +
            "WHERE t.to_project_id = #{projectId} AND t.status = 'APPROVED' AND t.deleted = 0 " +
            "UNION ALL " +
            "SELECT -COALESCE(SUM(d.quantity * d.unit_price), 0) AS net FROM biz_material_transfer t " +
            "JOIN biz_material_transfer_detail d ON d.transfer_id = t.id AND d.deleted = 0 " +
            "WHERE t.from_project_id = #{projectId} AND t.status = 'APPROVED' AND t.deleted = 0" +
            ") x")
    BigDecimal sumTransferNetForMaterial(@Param("projectId") Long projectId);

    /**
     * 按科目查询已签合同金额合计 — 劳务
     */
    @Select("SELECT COALESCE(SUM(contract_amount), 0) FROM biz_labor_contract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumContractAmountForLabor(@Param("projectId") Long projectId);

    /**
     * 按科目查询已签合同金额合计 — 机械
     */
    @Select("SELECT COALESCE(SUM(contract_amount), 0) FROM biz_machine_contract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumContractAmountForMachine(@Param("projectId") Long projectId);

    /**
     * 按科目查询已签合同金额合计 — 分包
     */
    @Select("SELECT COALESCE(SUM(contract_amount), 0) FROM biz_subcontract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumContractAmountForSubcontract(@Param("projectId") Long projectId);

    /**
     * 按科目查询已审批付款金额合计
     * biz_payment_apply 表中 contract_category 对应成本科目
     */
    @Select("SELECT COALESCE(SUM(payment_amount), 0) FROM biz_payment_apply " +
            "WHERE project_id = #{projectId} AND contract_category = #{costCategory} " +
            "AND status = 'APPROVED' AND deleted = 0")
    BigDecimal sumApprovedPaymentByCategory(@Param("projectId") Long projectId,
                                            @Param("costCategory") String costCategory);
}
