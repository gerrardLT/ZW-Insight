package com.zwinsight.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.material.domain.BizMaterialOutboundDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface BizMaterialOutboundDetailMapper extends BaseMapper<BizMaterialOutboundDetail> {

    /**
     * 按 BOQ 清单项汇总已出库领料总量（用于限额领料超耗校验）
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM biz_material_outbound_detail " +
            "WHERE boq_item_id = #{boqItemId} AND deleted = 0")
    BigDecimal sumQuantityByBoqItem(@Param("boqItemId") Long boqItemId);
}
