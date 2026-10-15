package com.zwinsight.purchase.mapper;

import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 同来源互斥：结算与退货均先锁入库主单，再读取占用（当前读）。 */
@Mapper
public interface PurchaseSourceMapper {
    @Select("SELECT id FROM biz_material_inbound WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Long lockInbound(Long id);

    @Select("SELECT d.id AS inboundDetailId,d.inbound_id AS inboundId,d.material_name AS materialName,d.specification,d.unit,d.quantity,d.unit_price AS unitPrice FROM biz_material_inbound_detail d WHERE d.inbound_id=#{id} AND d.deleted=0 ORDER BY d.id FOR UPDATE")
    List<Map<String,Object>> lines(Long id);

    @Select("SELECT inbound_id FROM biz_material_inbound_detail WHERE id=#{id} AND deleted=0")
    Long inboundId(Long id);

    @Select("SELECT COALESCE(SUM(d.quantity),0) FROM biz_purchase_settlement_detail d JOIN biz_purchase_settlement s ON s.id=d.settlement_id AND s.deleted=0 WHERE d.inbound_detail_id=#{lineId} AND d.deleted=0 AND s.id != #{excludeId} AND s.status IN ('SUBMITTED','APPROVED') FOR UPDATE")
    BigDecimal settled(@Param("lineId") Long lineId,@Param("excludeId") Long excludeId);

    @Select("SELECT COALESCE(SUM(d.quantity),0) FROM biz_material_outbound_detail d JOIN biz_material_outbound o ON o.id=d.outbound_id AND o.deleted=0 WHERE d.inbound_detail_id=#{lineId} AND d.deleted=0 AND o.outbound_type='RETURN' FOR UPDATE")
    BigDecimal returned(Long lineId);

    @Select("SELECT COUNT(*) FROM biz_material_outbound_detail d JOIN biz_material_outbound o ON o.id=d.outbound_id AND o.deleted=0 WHERE o.contract_id=#{contractId} AND o.outbound_type='RETURN' AND d.deleted=0 AND d.inbound_detail_id IS NULL FOR UPDATE")
    long unlinkedReturns(Long contractId);

    @Select("SELECT COUNT(*) FROM biz_purchase_settlement WHERE inbound_id=#{inboundId} AND deleted=0 AND id != #{excludeId} AND (#{legacy}=1 OR settlement_mode='LEGACY') AND status IN ('DRAFT','SUBMITTED','APPROVED') FOR UPDATE")
    long incompatible(@Param("inboundId") Long inboundId,@Param("excludeId") Long excludeId,@Param("legacy") int legacy);

    @Insert("INSERT INTO biz_purchase_settlement_detail(id,tenant_id,project_id,settlement_id,inbound_detail_id,quantity,unit_price,amount,deleted,version) VALUES(#{id},#{tenantId},#{projectId},#{settlementId},#{inboundDetailId},#{quantity},#{unitPrice},#{amount},0,0)")
    void insertLine(Map<String,Object> line);

    @Select("SELECT inbound_detail_id AS inboundDetailId,quantity,unit_price AS unitPrice,amount FROM biz_purchase_settlement_detail WHERE settlement_id=#{id} AND deleted=0 ORDER BY inbound_detail_id")
    List<Map<String,Object>> settlementLines(Long id);

    @Update("UPDATE biz_purchase_settlement_detail SET deleted=1 WHERE settlement_id=#{id} AND deleted=0")
    void deleteLines(Long id);

    @Update("UPDATE biz_purchase_settlement_detail SET deleted=1 WHERE project_id=#{id} AND deleted=0")
    void deleteProjectLines(Long id);
}
