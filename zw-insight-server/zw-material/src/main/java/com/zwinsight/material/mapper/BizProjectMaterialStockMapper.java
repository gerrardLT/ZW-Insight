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

    /** 入库原子累加（数量、累计入库与加权平均价在同一 SQL 内更新） */
    @Update("UPDATE biz_project_material_stock "
            + "SET avg_unit_price = ROUND((COALESCE(stock_quantity,0) * COALESCE(avg_unit_price,0) "
            + "+ #{qty} * #{price}) / NULLIF(COALESCE(stock_quantity,0) + #{qty},0), 4), "
            + "stock_quantity = COALESCE(stock_quantity,0) + #{qty}, "
            + "total_inbound = COALESCE(total_inbound,0) + #{qty} "
            + "WHERE id = #{id} AND deleted = 0")
    int addInbound(@Param("id") Long id, @Param("qty") BigDecimal qty, @Param("price") BigDecimal price);

    /** 删除已审批入库时原子回冲；库存与累计入库不足则拒绝 */
    @Update("UPDATE biz_project_material_stock "
            + "SET stock_quantity = stock_quantity - #{qty}, "
            + "total_inbound = total_inbound - #{qty} "
            + "WHERE id = #{id} AND deleted = 0 "
            + "AND stock_quantity >= #{qty} AND COALESCE(total_inbound,0) >= #{qty}")
    int revertInbound(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 调拨调出原子扣减 */
    @Update("UPDATE biz_project_material_stock "
            + "SET stock_quantity = stock_quantity - #{qty}, "
            + "total_transfer_out = COALESCE(total_transfer_out,0) + #{qty} "
            + "WHERE id = #{id} AND deleted = 0 AND stock_quantity >= #{qty}")
    int transferOut(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 调拨调入原子累加 */
    @Update("UPDATE biz_project_material_stock "
            + "SET stock_quantity = COALESCE(stock_quantity,0) + #{qty}, "
            + "total_transfer_in = COALESCE(total_transfer_in,0) + #{qty} "
            + "WHERE id = #{id} AND deleted = 0")
    int transferIn(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 盘点确认原子覆写库存（实盘数量已由服务校验非负） */
    @Update("UPDATE biz_project_material_stock SET stock_quantity = #{actualQty} "
            + "WHERE id = #{id} AND deleted = 0 AND #{actualQty} >= 0")
    int setInventoryQuantity(@Param("id") Long id, @Param("actualQty") BigDecimal actualQty);
}
