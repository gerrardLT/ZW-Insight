package com.zwinsight.finance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 结算数据汇总查询 Mapper
 * <p>
 * 跨模块查询各类合同的结算总额和累计收付款数据，
 * 用于项目最终结算单的自动数据汇总。
 * 直接使用 SQL 查询避免模块间循环依赖。
 * </p>
 */
@Mapper
public interface SettlementDataMapper {

    /**
     * 查询分包合同累计结算总额
     */
    @Select("SELECT COALESCE(SUM(cumulative_settlement), 0) FROM biz_subcontract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumSubcontractSettlement(@Param("projectId") Long projectId);

    /**
     * 查询劳务合同累计结算总额
     */
    @Select("SELECT COALESCE(SUM(cumulative_settlement), 0) FROM biz_labor_contract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumLaborSettlement(@Param("projectId") Long projectId);

    /**
     * 查询材料（采购）合同累计结算总额
     */
    @Select("SELECT COALESCE(SUM(cumulative_settlement), 0) FROM biz_purchase_contract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumMaterialSettlement(@Param("projectId") Long projectId);

    /**
     * 查询机械合同累计结算总额
     */
    @Select("SELECT COALESCE(SUM(cumulative_settlement), 0) FROM biz_machine_contract " +
            "WHERE project_id = #{projectId} AND status = 'EFFECTIVE' AND deleted = 0")
    BigDecimal sumMachineSettlement(@Param("projectId") Long projectId);

    /**
     * 查询项目累计付款总额（已审批的付款申请）
     */
    @Select("SELECT COALESCE(SUM(payment_amount), 0) FROM biz_payment_apply " +
            "WHERE project_id = #{projectId} AND status = 'APPROVED' AND deleted = 0")
    BigDecimal sumPaymentByProject(@Param("projectId") Long projectId);

    /**
     * 查询项目累计收款总额
     */
    @Select("SELECT COALESCE(SUM(receive_amount), 0) FROM biz_payment_received " +
            "WHERE project_id = #{projectId} AND deleted = 0")
    BigDecimal sumReceivedByProject(@Param("projectId") Long projectId);

    /**
     * 查询项目累计开票总额（已审批的开票申请）
     */
    @Select("SELECT COALESCE(SUM(invoice_amount), 0) FROM biz_invoice_apply " +
            "WHERE project_id = #{projectId} AND status = 'APPROVED' AND deleted = 0")
    BigDecimal sumInvoicedByProject(@Param("projectId") Long projectId);
}
