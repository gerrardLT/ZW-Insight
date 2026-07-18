package com.zwinsight.purchase.readmodel;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 材料入库单只读 Mapper（跨模块只读查询 biz_material_inbound）。
 * <p>
 * 仅用于采购结算读取入库依据，不做任何写操作。
 * tenant_id 条件由 TenantLineInnerInterceptor 自动注入（biz_ 前缀表参与租户隔离），
 * 逻辑删除条件 deleted=0 需在 SQL 中显式声明（原生 @Select 不走 @TableLogic）。
 * </p>
 */
@Mapper
public interface MaterialInboundReadMapper {

    /**
     * 按入库单ID查询（结算创建时带入入库金额）
     */
    @Select("SELECT id, project_id, contract_id, inbound_code, inbound_date, total_amount, status "
            + "FROM biz_material_inbound WHERE id = #{id} AND deleted = 0")
    MaterialInboundView selectById(Long id);

    /**
     * 查询指定合同下已审批的入库单列表（供结算时选择可结算入库批次）
     */
    @Select("SELECT id, project_id, contract_id, inbound_code, inbound_date, total_amount, status "
            + "FROM biz_material_inbound "
            + "WHERE contract_id = #{contractId} AND status = 'APPROVED' AND deleted = 0 "
            + "ORDER BY inbound_date DESC")
    List<MaterialInboundView> selectApprovedByContract(Long contractId);
}
