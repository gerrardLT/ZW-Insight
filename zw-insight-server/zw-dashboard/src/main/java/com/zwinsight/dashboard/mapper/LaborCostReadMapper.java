package com.zwinsight.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 劳务人工支出只读聚合 Mapper（C5，V2026_110 后批次）。
 * <p>
 * zw-dashboard 不依赖 zw-labor，跨模块取数按仓库既有惯例走只读跨表 SQL
 * （先例：BizProjectMapper.countTenderRegisters）。只 SELECT，不写入。
 * 工资性支出（工资单+点工）与劳务结算并列呈现，供人工成本口径对照——
 * <b>工资性支出未入 CBS 权责成本（会计政策待定），只读对照不写账户</b>。
 * </p>
 */
@Mapper
public interface LaborCostReadMapper {

    /** 已审批工资单应发合计（biz_labor_payroll.total_settlement，按单据状态 APPROVED） */
    @Select("SELECT COALESCE(SUM(p.total_settlement), 0) FROM biz_labor_payroll p " +
            "WHERE p.project_id = #{projectId} AND p.deleted = 0 AND p.status = 'APPROVED'")
    BigDecimal sumApprovedPayroll(@Param("projectId") Long projectId);

    /** 已审批点工单金额合计（biz_work_order.total_amount） */
    @Select("SELECT COALESCE(SUM(w.total_amount), 0) FROM biz_work_order w " +
            "WHERE w.project_id = #{projectId} AND w.deleted = 0 AND w.status = 'APPROVED'")
    BigDecimal sumApprovedWorkOrders(@Param("projectId") Long projectId);

    /** 劳务合同累计结算合计（权责口径，已入 CBS） */
    @Select("SELECT COALESCE(SUM(c.cumulative_settlement), 0) FROM biz_labor_contract c " +
            "WHERE c.project_id = #{projectId} AND c.deleted = 0")
    BigDecimal sumLaborSettlement(@Param("projectId") Long projectId);
}
