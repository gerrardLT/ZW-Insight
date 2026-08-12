package com.zwinsight.purchase.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 采购合同付款扣减 Mapper（退款回冲专用）
 * <p>
 * 与 zw-finance ContractPayableMapper 同模式：纯 SQL 回写方法不挂 @DataPermission，
 * 避免审批回调上下文中数据权限拦截器给 UPDATE 追加行条件导致扣减静默失效。
 * </p>
 * <p>
 * 2026-08-13 修复（批次二 L4 stage_9K 500 事故）：原 MaterialRefundService 经
 * BizExpenseContractMapper.deductPaidAmount 扣减 biz_expense_contract，而采购付款
 * 回写的是 biz_purchase_contract（PaymentApplyService.addCumulativePaid 路由），
 * 扣减长期命中 0 行静默无效；本 Mapper 将退款扣减对齐到真实付款表，
 * 并以 WHERE 条件原子保证「退款额 ≤ 累计已付」下限守卫。
 * </p>
 */
@Mapper
public interface PurchaseContractPayMapper {

    /**
     * 查询采购合同累计已付款（合同不存在返回 null）
     */
    @Select("SELECT cumulative_paid FROM biz_purchase_contract WHERE id = #{id} AND deleted = 0")
    BigDecimal selectCumulativePaid(@Param("id") Long id);

    /**
     * 原子扣减累计已付款（下限守卫：cumulative_paid 不足或合同不存在时命中 0 行）
     *
     * @return 受影响行数，0 表示合同不存在或退款额超过累计已付
     */
    @Update("UPDATE biz_purchase_contract " +
            "SET cumulative_paid = COALESCE(cumulative_paid, 0) - #{amount}, updated_at = NOW() " +
            "WHERE id = #{id} AND deleted = 0 AND COALESCE(cumulative_paid, 0) >= #{amount}")
    int deductPaid(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
