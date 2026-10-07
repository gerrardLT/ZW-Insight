package com.zwinsight.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface BizProjectMaterialStockMapper extends BaseMapper<BizProjectMaterialStock> {

    /**
     * 原子扣减库存（带 stock_quantity >= #{qty} 非负守卫，PI-1）
     *
     * @param id          库存行ID
     * @param qty         出库/调出数量
     * @param isPick      是否为领料（true 增加 total_outbound，false 增加 total_return）
     * @return 影响行数（0 表示库存不足）
     */
    @Update("<script>"
            + "UPDATE biz_project_material_stock "
            + "SET stock_quantity = stock_quantity - #{qty}, "
            + "<choose>"
            + "  <when test='isPick'>total_outbound = COALESCE(total_outbound, 0) + #{qty}</when>"
            + "  <otherwise>total_return = COALESCE(total_return, 0) + #{qty}</otherwise>"
            + "</choose> "
            + "WHERE id = #{id} AND deleted = 0 AND stock_quantity &gt;= #{qty}"
            + "</script>")
    int deductStock(@Param("id") Long id, @Param("qty") BigDecimal qty, @Param("isPick") boolean isPick);

    /**
     * 原子回补库存（用于删除出库单时的对称回填，PI-1）
     */
    @Update("<script>"
            + "UPDATE biz_project_material_stock "
            + "SET stock_quantity = stock_quantity + #{qty}, "
            + "<choose>"
            + "  <when test='isPick'>total_outbound = GREATEST(0, COALESCE(total_outbound, 0) - #{qty})</when>"
            + "  <otherwise>total_return = GREATEST(0, COALESCE(total_return, 0) - #{qty})</otherwise>"
            + "</choose> "
            + "WHERE id = #{id} AND deleted = 0"
            + "</script>")
    int revertOutbound(@Param("id") Long id, @Param("qty") BigDecimal qty, @Param("isPick") boolean isPick);
}
