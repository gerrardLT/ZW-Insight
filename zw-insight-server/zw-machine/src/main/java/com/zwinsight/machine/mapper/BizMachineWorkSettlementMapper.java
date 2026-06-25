package com.zwinsight.machine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.machine.domain.BizMachineWorkSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 机械工作量结算单 Mapper
 */
@Mapper
public interface BizMachineWorkSettlementMapper extends BaseMapper<BizMachineWorkSettlement> {

    /**
     * 检查同一项目内是否存在周期重叠的结算单
     *
     * @param projectId   项目ID
     * @param periodStart 周期开始
     * @param periodEnd   周期结束
     * @param excludeId   排除的结算单ID（用于编辑场景）
     * @return 重叠记录数
     */
    @Select("<script>" +
            "SELECT COUNT(1) FROM biz_machine_work_settlement " +
            "WHERE project_id = #{projectId} " +
            "AND deleted = 0 " +
            "AND period_start &lt;= #{periodEnd} " +
            "AND period_end &gt;= #{periodStart} " +
            "<if test='excludeId != null'> AND id != #{excludeId} </if>" +
            "</script>")
    int countOverlapping(@Param("projectId") Long projectId,
                         @Param("periodStart") LocalDate periodStart,
                         @Param("periodEnd") LocalDate periodEnd,
                         @Param("excludeId") Long excludeId);

    /**
     * 获取当月结算单最大序号
     *
     * @param codePrefix 编号前缀，如 "JXJS-202607-"
     * @return 当前最大编号，无记录时返回 null
     */
    @Select("SELECT MAX(settlement_code) FROM biz_machine_work_settlement " +
            "WHERE settlement_code LIKE CONCAT(#{codePrefix}, '%') AND deleted = 0")
    String getMaxCodeByPrefix(@Param("codePrefix") String codePrefix);
}
