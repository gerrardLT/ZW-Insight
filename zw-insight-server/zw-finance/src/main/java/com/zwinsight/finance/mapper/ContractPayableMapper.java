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
}
