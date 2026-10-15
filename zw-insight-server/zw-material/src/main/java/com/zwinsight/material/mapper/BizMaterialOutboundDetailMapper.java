package com.zwinsight.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.material.domain.BizMaterialOutboundDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface BizMaterialOutboundDetailMapper extends BaseMapper<BizMaterialOutboundDetail> {

    /** 仅发现锁目标，不得将此快照用于库存反冲。 */
    @Select("SELECT DISTINCT i.inbound_id FROM biz_material_outbound_detail d JOIN biz_material_inbound_detail i ON i.id=d.inbound_detail_id AND i.deleted=0 WHERE d.outbound_id=#{id} AND d.deleted=0 ORDER BY i.inbound_id")
    java.util.List<Long> discoverInboundIds(Long id);

    /** 主单锁后当前读，避免 REPEATABLE READ 复用发现阶段的旧快照。 */
    @Select("SELECT * FROM biz_material_outbound_detail WHERE outbound_id=#{id} AND deleted=0 ORDER BY id FOR UPDATE")
    java.util.List<BizMaterialOutboundDetail> lockActiveDetails(Long id);

    /**
     * 按 BOQ 清单项汇总已出库领料总量（用于限额领料超耗校验）
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM biz_material_outbound_detail " +
            "WHERE boq_item_id = #{boqItemId} AND deleted = 0")
    BigDecimal sumQuantityByBoqItem(@Param("boqItemId") Long boqItemId);
}
