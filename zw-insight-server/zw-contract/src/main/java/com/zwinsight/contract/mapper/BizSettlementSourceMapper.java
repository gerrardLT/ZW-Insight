package com.zwinsight.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.contract.domain.BizSettlementSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 过程结算来源明细 Mapper（V2026_108）。
 * <p>注意：本接口必须带 {@code @Mapper}——项目 {@code @MapperScan} 只注册带注解的接口，
 * 漏注解会让依赖本 Mapper 的 Service 启动失败（2026-10-15 生产事故教训）。</p>
 */
@Mapper
public interface BizSettlementSourceMapper extends BaseMapper<BizSettlementSource> {

    /**
     * 来源占用计数：该来源被「除 excludeId 外」的 SUBMITTED/APPROVED 结算引用的行数。
     * 提交与审批生效前双重校验，防止同一产值/签证被两张有效结算重复确认。
     */
    @Select("SELECT COUNT(*) FROM biz_settlement_source s " +
            "JOIN biz_final_settlement f ON s.settlement_id = f.id AND f.deleted = 0 " +
            "WHERE s.deleted = 0 AND s.source_type = #{sourceType} AND s.source_id = #{sourceId} " +
            "AND f.status IN ('SUBMITTED', 'APPROVED') AND s.settlement_id != #{excludeSettlementId}")
    int countSourceOccupied(@Param("sourceType") String sourceType,
                            @Param("sourceId") Long sourceId,
                            @Param("excludeSettlementId") Long excludeSettlementId);
}
