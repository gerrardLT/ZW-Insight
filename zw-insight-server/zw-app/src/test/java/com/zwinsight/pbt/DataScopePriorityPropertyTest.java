package com.zwinsight.pbt;

import com.zwinsight.common.datapermission.DataScopeEnum;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import org.assertj.core.api.Assertions;

import java.util.List;

/**
 * Property 2：多角色数据范围优先级计算
 * <p>
 * 验证 getEffectiveScope 始终返回给定角色列表中优先级最高的数据范围。
 * <p>
 * Validates: Requirements 1.2
 */
@Tag("Feature: p1-system-integrity, Property 2: 多角色数据范围优先级计算")
class DataScopePriorityPropertyTest {

    /**
     * 核心业务逻辑：多角色场景取最大数据范围
     * 从角色对应的 DataScopeEnum 列表中返回优先级最高的那个。
     */
    static DataScopeEnum getEffectiveScope(List<DataScopeEnum> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return DataScopeEnum.SELF; // 默认最小权限
        }
        return scopes.stream()
                .reduce((a, b) -> a.getPriority() >= b.getPriority() ? a : b)
                .orElse(DataScopeEnum.SELF);
    }

    @Property(tries = 100)
    void effectiveScope_alwaysReturnsMaxPriority(
            @ForAll("nonEmptyScopeList") List<DataScopeEnum> scopes) {
        DataScopeEnum result = getEffectiveScope(scopes);

        // 结果的优先级应该等于列表中最大的优先级
        int maxPriority = scopes.stream()
                .mapToInt(DataScopeEnum::getPriority)
                .max()
                .orElse(1);

        Assertions.assertThat(result.getPriority()).isEqualTo(maxPriority);
    }

    @Property(tries = 100)
    void effectiveScope_isAlwaysContainedInInput(
            @ForAll("nonEmptyScopeList") List<DataScopeEnum> scopes) {
        DataScopeEnum result = getEffectiveScope(scopes);
        Assertions.assertThat(scopes).contains(result);
    }

    @Property(tries = 100)
    void effectiveScope_isIdempotent(
            @ForAll("nonEmptyScopeList") List<DataScopeEnum> scopes) {
        DataScopeEnum first = getEffectiveScope(scopes);
        DataScopeEnum second = getEffectiveScope(scopes);
        Assertions.assertThat(first).isEqualTo(second);
    }

    @Property(tries = 100)
    void singleScope_returnsSelf(@ForAll DataScopeEnum scope) {
        DataScopeEnum result = getEffectiveScope(List.of(scope));
        Assertions.assertThat(result).isEqualTo(scope);
    }

    @Provide
    Arbitrary<List<DataScopeEnum>> nonEmptyScopeList() {
        return Arbitraries.of(DataScopeEnum.class).list().ofMinSize(1).ofMaxSize(10);
    }
}
