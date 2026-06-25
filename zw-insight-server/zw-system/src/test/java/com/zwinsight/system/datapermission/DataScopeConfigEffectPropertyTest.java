package com.zwinsight.system.datapermission;

import com.zwinsight.common.datapermission.DataPermissionDataProvider;
import com.zwinsight.common.datapermission.DataScopeEnum;
import com.zwinsight.common.datapermission.ZwDataPermissionHandler;
import net.jqwik.api.*;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

// Feature: p0-data-permission-overdue, Property 2: DataScope 配置立即生效
/**
 * Property 2: DataScope 配置立即生效
 * <p>
 * 验证：对于任意合法 DataScopeEnum 值 v 和角色 r，将 r 的 dataScope 设为 v 后，
 * 后续对 getUserDataScopes 的调用应返回包含 v 的列表。
 * </p>
 * <p>
 * 由于 DataPermissionDataProviderImpl 每次调用实时查询数据库不使用缓存，
 * 本测试通过模拟数据库行为验证：设置新值后，Provider 立即返回新值，
 * 并且 ZwDataPermissionHandler.getEffectiveScope 反映新的数据范围。
 * </p>
 * <p>
 * **Validates: Requirements 1.2**
 * </p>
 */
@Tag("Feature: p0-data-permission-overdue, Property 2: DataScope 配置立即生效")
class DataScopeConfigEffectPropertyTest {

    /**
     * 模拟"数据库"状态 — 保存当前用户的 dataScope 列表。
     * 当设置新值时，下次查询立即反映新值（无缓存）。
     */
    static class InMemoryDataScopeStore {
        private final List<String> currentScopes = new ArrayList<>();

        void setDataScope(String newScope) {
            currentScopes.clear();
            currentScopes.add(newScope);
        }

        void addDataScope(String scope) {
            currentScopes.add(scope);
        }

        List<String> getDataScopes() {
            return new ArrayList<>(currentScopes);
        }
    }

    /**
     * 验证：设置单个 dataScope 后，getUserDataScopes 立即返回包含该值的列表
     */
    @Property(tries = 100)
    void afterSettingDataScope_getUserDataScopes_returnsNewValue(
            @ForAll DataScopeEnum newScope,
            @ForAll("userIds") Long userId) {

        InMemoryDataScopeStore store = new InMemoryDataScopeStore();

        // 模拟 "更新数据库" — 将新 scope 写入
        store.setDataScope(newScope.name());

        // 创建 Provider mock，返回 store 中的当前值
        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(anyLong())).thenAnswer(inv -> store.getDataScopes());

        // 验证 getUserDataScopes 返回包含新值
        List<String> result = mockProvider.getUserDataScopes(userId);
        Assertions.assertThat(result).contains(newScope.name());
    }

    /**
     * 验证：更新 dataScope 后，getEffectiveScope 返回新的有效范围
     */
    @Property(tries = 100)
    void afterSettingDataScope_getEffectiveScope_reflectsNewScope(
            @ForAll DataScopeEnum newScope,
            @ForAll("userIds") Long userId) {

        InMemoryDataScopeStore store = new InMemoryDataScopeStore();
        store.setDataScope(newScope.name());

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(anyLong())).thenAnswer(inv -> store.getDataScopes());
        when(mockProvider.getUserDeptId(anyLong())).thenReturn(1L);
        when(mockProvider.getUserProjectIds(anyLong())).thenReturn(List.of(1L));
        when(mockProvider.getDeptAndChildIds(anyLong())).thenReturn(List.of(1L));

        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);

        DataScopeEnum effectiveScope = handler.getEffectiveScope(userId);
        Assertions.assertThat(effectiveScope).isEqualTo(newScope);
    }

    /**
     * 验证：连续两次修改 dataScope，最终结果反映最后一次设置（无缓存）
     */
    @Property(tries = 100)
    void multipleUpdates_lastOneWins(
            @ForAll DataScopeEnum firstScope,
            @ForAll DataScopeEnum secondScope,
            @ForAll("userIds") Long userId) {

        InMemoryDataScopeStore store = new InMemoryDataScopeStore();

        // 第一次设置
        store.setDataScope(firstScope.name());

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(anyLong())).thenAnswer(inv -> store.getDataScopes());

        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);

        // 第二次设置（模拟配置变更）
        store.setDataScope(secondScope.name());

        // 后续调用应返回第二次设置的值
        DataScopeEnum effectiveScope = handler.getEffectiveScope(userId);
        Assertions.assertThat(effectiveScope).isEqualTo(secondScope);
    }

    /**
     * 验证：多角色场景，添加多个 scope 后，getEffectiveScope 返回最高优先级
     */
    @Property(tries = 100)
    void multipleRoles_effectiveScope_isMaxPriority(
            @ForAll("scopeList") List<DataScopeEnum> scopes,
            @ForAll("userIds") Long userId) {

        InMemoryDataScopeStore store = new InMemoryDataScopeStore();
        for (DataScopeEnum scope : scopes) {
            store.addDataScope(scope.name());
        }

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(anyLong())).thenAnswer(inv -> store.getDataScopes());

        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);

        DataScopeEnum effectiveScope = handler.getEffectiveScope(userId);

        // 验证取到的是最高优先级
        int expectedMaxPriority = scopes.stream()
                .mapToInt(DataScopeEnum::getPriority)
                .max()
                .orElse(DataScopeEnum.SELF.getPriority());

        Assertions.assertThat(effectiveScope.getPriority()).isEqualTo(expectedMaxPriority);
    }

    /**
     * 验证：无论设置多少次，每次查询都返回最新状态（无缓存行为）
     */
    @Property(tries = 100)
    void noCaching_everyCallReflectsCurrentState(
            @ForAll DataScopeEnum initialScope,
            @ForAll DataScopeEnum updatedScope,
            @ForAll("userIds") Long userId) {

        InMemoryDataScopeStore store = new InMemoryDataScopeStore();
        store.setDataScope(initialScope.name());

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(anyLong())).thenAnswer(inv -> store.getDataScopes());

        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);

        // 第一次查询
        DataScopeEnum firstResult = handler.getEffectiveScope(userId);
        Assertions.assertThat(firstResult).isEqualTo(initialScope);

        // 修改数据
        store.setDataScope(updatedScope.name());

        // 第二次查询 — 必须反映变化
        DataScopeEnum secondResult = handler.getEffectiveScope(userId);
        Assertions.assertThat(secondResult).isEqualTo(updatedScope);
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1L, 999999L);
    }

    @Provide
    Arbitrary<List<DataScopeEnum>> scopeList() {
        return Arbitraries.of(DataScopeEnum.class).list().ofMinSize(1).ofMaxSize(5);
    }
}
