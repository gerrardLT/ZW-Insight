package com.zwinsight.pbt;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.datapermission.DataPermissionDataProvider;
import com.zwinsight.common.datapermission.ZwDataPermissionHandler;
import net.jqwik.api.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

// Feature: p0-data-permission-overdue, Property 4: 未注解方法不过滤
/**
 * Property 4: 未注解方法不过滤
 * <p>
 * 验证：对于任何未标注 @DataPermission 注解的 Mapper 方法，
 * ZwDataPermissionHandler.getSqlSegment 应返回 null，不追加任何数据权限条件。
 * </p>
 * <p>
 * **Validates: Requirements 3.4**
 * </p>
 */
@Tag("Feature: p0-data-permission-overdue, Property 4: 未注解方法不过滤")
class UnAnnotatedMethodPropertyTest {

    /**
     * 创建一个 handler 实例（使用 mock DataProvider）
     */
    private ZwDataPermissionHandler createHandler() {
        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(anyLong())).thenReturn(List.of("ALL"));
        when(mockProvider.getUserDeptId(anyLong())).thenReturn(1L);
        when(mockProvider.getUserProjectIds(anyLong())).thenReturn(List.of(1L, 2L));
        when(mockProvider.getDeptAndChildIds(anyLong())).thenReturn(List.of(1L, 2L, 3L));
        return new ZwDataPermissionHandler(mockProvider);
    }

    /**
     * 使用不存在的类名模拟未标注注解的 mappedStatementId。
     * 当 handler 无法加载类或方法上无 @DataPermission 注解时，应返回 null。
     */
    @Property(tries = 100)
    void nonExistentMapper_returnsNull(
            @ForAll("randomPackagePaths") String mappedStatementId) {

        ZwDataPermissionHandler handler = createHandler();
        Table table = new Table("some_table");

        // 设置用户上下文以确保不是因为缺少用户上下文才返回 null
        SecurityContextHolder.setUserId(1L);
        try {
            Expression result = handler.getSqlSegment(table, null, mappedStatementId);
            Assertions.assertThat(result).isNull();
        } finally {
            SecurityContextHolder.clear();
        }
    }

    /**
     * 使用系统管理模块的 Mapper 路径，验证系统管理模块不做过滤。
     * 这些路径以 com.zwinsight.system.mapper. 开头。
     */
    @Property(tries = 100)
    void systemMapperModule_alwaysReturnsNull(
            @ForAll("systemMapperIds") String mappedStatementId) {

        ZwDataPermissionHandler handler = createHandler();
        Table table = new Table("sys_user");

        SecurityContextHolder.setUserId(1L);
        try {
            Expression result = handler.getSqlSegment(table, null, mappedStatementId);
            Assertions.assertThat(result).isNull();
        } finally {
            SecurityContextHolder.clear();
        }
    }

    /**
     * 验证空字符串的 mappedStatementId 时处理正确（返回 null）
     */
    @Property(tries = 100)
    void emptyMappedStatementId_returnsNull(@ForAll("userIds") Long userId) {
        ZwDataPermissionHandler handler = createHandler();
        Table table = new Table("any_table");

        SecurityContextHolder.setUserId(userId);
        try {
            Expression result = handler.getSqlSegment(table, null, "");
            Assertions.assertThat(result).isNull();
        } finally {
            SecurityContextHolder.clear();
        }
    }

    /**
     * 使用不包含点号的 mappedStatementId（无法解析为 className.methodName），
     * 验证返回 null。
     */
    @Property(tries = 100)
    void invalidFormatMappedStatementId_returnsNull(
            @ForAll("invalidIds") String mappedStatementId) {

        ZwDataPermissionHandler handler = createHandler();
        Table table = new Table("biz_table");

        SecurityContextHolder.setUserId(1L);
        try {
            Expression result = handler.getSqlSegment(table, null, mappedStatementId);
            Assertions.assertThat(result).isNull();
        } finally {
            SecurityContextHolder.clear();
        }
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1L, 999999L);
    }

    /**
     * 生成随机的不存在的包路径，模拟未标注注解的 Mapper 方法
     */
    @Provide
    Arbitrary<String> randomPackagePaths() {
        Arbitrary<String> packages = Arbitraries.of(
                "com.zwinsight.nonexistent.mapper",
                "com.example.unknown.mapper",
                "org.test.fake.mapper",
                "com.zwinsight.business.fake.mapper"
        );
        Arbitrary<String> classNames = Arbitraries.of(
                "FakeMapper", "NonExistMapper", "MockMapper",
                "UnknownMapper", "TestMapper", "DummyMapper"
        );
        Arbitrary<String> methodNames = Arbitraries.of(
                "selectList", "findById", "queryPage",
                "selectAll", "getById", "listByCondition"
        );

        return Combinators.combine(packages, classNames, methodNames)
                .as((pkg, cls, method) -> pkg + "." + cls + "." + method);
    }

    /**
     * 生成系统管理模块的 Mapper 路径
     */
    @Provide
    Arbitrary<String> systemMapperIds() {
        Arbitrary<String> mapperNames = Arbitraries.of(
                "SysUserMapper", "SysRoleMapper", "SysOrgMapper",
                "SysMenuMapper", "SysDictMapper", "SysConfigMapper"
        );
        Arbitrary<String> methodNames = Arbitraries.of(
                "selectList", "selectById", "selectPage",
                "insert", "update", "delete"
        );

        return Combinators.combine(mapperNames, methodNames)
                .as((mapper, method) -> "com.zwinsight.system.mapper." + mapper + "." + method);
    }

    /**
     * 生成不包含点号的无效 mappedStatementId
     */
    @Provide
    Arbitrary<String> invalidIds() {
        return Arbitraries.of(
                "noDotsHere", "simpleString", "noclassnomethod",
                "abc", "xyz", "hello"
        );
    }
}
