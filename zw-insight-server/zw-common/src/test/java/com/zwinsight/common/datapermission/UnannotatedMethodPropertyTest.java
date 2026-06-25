package com.zwinsight.common.datapermission;

// Feature: p0-data-permission-overdue, Property 4: 未注解方法不过滤
import net.jqwik.api.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Property 4: 未注解方法不过滤
 * <p>
 * 验证：对于未标注 @DataPermission 注解的 Mapper 方法，
 * ZwDataPermissionHandler.getSqlSegment 应返回 null，不追加任何数据权限条件。
 * </p>
 * <p>
 * 测试策略：
 * 1. 使用不存在的类名作为 mappedStatementId（ClassNotFoundException → 返回 null）
 * 2. 使用存在但无 @DataPermission 注解的类方法作为 mappedStatementId
 * 3. 使用 null mappedStatementId
 * </p>
 * <p>
 * **Validates: Requirements 3.4**
 * </p>
 */
@Tag("Feature: p0-data-permission-overdue, Property 4: 未注解方法不过滤")
class UnannotatedMethodPropertyTest {

    /**
     * Property: 不存在的类 → getSqlSegment 返回 null
     * <p>
     * 随机生成不存在的 mappedStatementId（格式：随机包名.类名.方法名），
     * 由于 Class.forName 无法加载这些类，findAnnotation 返回 null，
     * getSqlSegment 应返回 null。
     * </p>
     */
    @Property(tries = 100)
    void nonExistentClass_returnsNull(
            @ForAll("randomMappedStatementIds") String mappedStatementId) {

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(anyLong())).thenReturn(List.of("SELF"));

        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);
        Table table = new Table("biz_inspection");

        // 设置用户上下文（确保不会因为用户上下文缺失而抛异常）
        com.zwinsight.common.config.SecurityContextHolder.setUserId(1L);
        try {
            Expression result = handler.getSqlSegment(table, null, mappedStatementId);
            Assertions.assertThat(result).isNull();
        } finally {
            com.zwinsight.common.config.SecurityContextHolder.clear();
        }
    }

    /**
     * Property: 存在的类但方法无 @DataPermission 注解 → getSqlSegment 返回 null
     * <p>
     * 使用已知的无注解类（如 java.lang.String）的方法名组合为 mappedStatementId，
     * 验证 handler 能正确识别无注解情况并返回 null。
     * </p>
     */
    @Property(tries = 100)
    void existingClassWithoutAnnotation_returnsNull(
            @ForAll("methodNamesForExistingClasses") String mappedStatementId) {

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(anyLong())).thenReturn(List.of("DEPT"));

        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);
        Table table = new Table("biz_inspection");

        com.zwinsight.common.config.SecurityContextHolder.setUserId(1L);
        try {
            Expression result = handler.getSqlSegment(table, null, mappedStatementId);
            Assertions.assertThat(result).isNull();
        } finally {
            com.zwinsight.common.config.SecurityContextHolder.clear();
        }
    }

    /**
     * Property: null mappedStatementId → getSqlSegment 返回 null
     */
    @Property(tries = 100)
    void nullMappedStatementId_returnsNull(
            @ForAll("userIds") Long userId) {

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);
        Table table = new Table("biz_inspection");

        com.zwinsight.common.config.SecurityContextHolder.setUserId(userId);
        try {
            Expression result = handler.getSqlSegment(table, null, null);
            Assertions.assertThat(result).isNull();
        } finally {
            com.zwinsight.common.config.SecurityContextHolder.clear();
        }
    }

    /**
     * Property: 系统管理模块的 mappedStatementId → getSqlSegment 返回 null
     * <p>
     * 以 com.zwinsight.system.mapper. 为前缀的任何 mappedStatementId
     * 直接跳过数据权限过滤。
     * </p>
     */
    @Property(tries = 100)
    void systemMapperPrefix_returnsNull(
            @ForAll("systemMapperIds") String mappedStatementId) {

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);
        Table table = new Table("sys_role");

        com.zwinsight.common.config.SecurityContextHolder.setUserId(1L);
        try {
            Expression result = handler.getSqlSegment(table, null, mappedStatementId);
            Assertions.assertThat(result).isNull();
        } finally {
            com.zwinsight.common.config.SecurityContextHolder.clear();
        }
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1L, 999999L);
    }

    /**
     * 生成随机的不存在的 mappedStatementId
     * 格式：com.random.package.ClassName.methodName
     */
    @Provide
    Arbitrary<String> randomMappedStatementIds() {
        Arbitrary<String> packages = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(10)
                .map(String::toLowerCase);
        Arbitrary<String> classNames = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase());
        Arbitrary<String> methodNames = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(12)
                .map(String::toLowerCase);

        return Combinators.combine(packages, classNames, methodNames)
                .as((pkg, cls, method) -> "com.nonexistent." + pkg + "." + cls + "." + method);
    }

    /**
     * 生成使用存在类（无 @DataPermission 注解）的 mappedStatementId
     * 使用 JDK 标准类确保类可加载但无 @DataPermission 注解
     */
    @Provide
    Arbitrary<String> methodNamesForExistingClasses() {
        // 使用已知的无 @DataPermission 注解的标准 JDK 类
        Arbitrary<String> existingClasses = Arbitraries.of(
                "java.lang.String",
                "java.lang.Integer",
                "java.lang.Long",
                "java.util.ArrayList",
                "java.util.HashMap",
                "java.util.LinkedList"
        );
        Arbitrary<String> methodNames = Arbitraries.of(
                "toString", "hashCode", "equals", "size", "get", "put",
                "add", "remove", "contains", "isEmpty", "length", "charAt"
        );

        return Combinators.combine(existingClasses, methodNames)
                .as((cls, method) -> cls + "." + method);
    }

    /**
     * 生成系统管理模块前缀的 mappedStatementId
     */
    @Provide
    Arbitrary<String> systemMapperIds() {
        Arbitrary<String> mapperNames = Arbitraries.strings()
                .alpha().ofMinLength(5).ofMaxLength(15)
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase() + "Mapper");
        Arbitrary<String> methodNames = Arbitraries.of(
                "selectList", "selectById", "selectPage", "insert", "updateById",
                "deleteById", "selectCount", "selectOne"
        );

        return Combinators.combine(mapperNames, methodNames)
                .as((mapper, method) -> "com.zwinsight.system.mapper." + mapper + "." + method);
    }
}
