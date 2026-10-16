package com.zwinsight.purchase.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 材料结算容差配置 Mapper（V2026_111，C4）。
 * <p>租户级单行：容差上限 = max(来源金额 × pct%, abs)。默认 0 = 任何价差都须填修订原因。
 * 必须带 {@code @Mapper}：@MapperScan 只注册带注解接口。</p>
 */
@Mapper
public interface BizSettlementToleranceMapper {

    @Select("SELECT pct_tolerance AS pctTolerance, abs_tolerance AS absTolerance " +
            "FROM biz_settlement_tolerance WHERE tenant_id = #{tenantId} AND deleted = 0")
    Map<String, Object> tolerance(Long tenantId);
}
