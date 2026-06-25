package com.zwinsight.system.datapermission;

import com.zwinsight.common.datapermission.DataPermissionDataProvider;
import com.zwinsight.common.datapermission.ZwDataPermissionHandler;
import net.jqwik.api.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

// Feature: p0-data-permission-overdue, Property 4: 未注解方法不过滤
/**
 * Property 4: 未注解方法不过滤
 * <p>
 * 验证：对于任意未标注 @DataPermission 注解的 Mapper 方法（即非系统管理模块前缀的随机 mappedStatementId），
 * ZwDataPermissionHandler.getSqlSegment 应返回 null，不追加任何数据权限条件。
 * </p>
 * <p>
 * **Validates: Requirements 3.4**
 * </p>
 */
@Tag("Feature: p0-data-permission-overdue, Property 4: 未注解方法不过滤")
class DataPermissionAnnotationPropertyTest {

    /**
     * 生成随机的 mappedStatementId（使用真实存在于classpath的类但未标注@DataPermission），
     * 验证 getSqlSegment 对这些方法返回 null。
     */
    @Property(tries = 200)
    void unannotatedMethod_getSqlSegment_returnsNull(
            @ForAll("unannotatedClassStatementIds") String mappedStatementId) {

        // 每次创建新 handler 避免缓存问题
        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);

        Table table = new Table("some_table");
        Expression where = null;

        // 调用 getSqlSegment — 未注解的方法应返回 null
        Expression result = handler.getSqlSegment(table, where, mappedStatementId);

        Assertions.assertThat(result).isNull();

        // 验证 DataProvider 没有被调用（因为方法没有 @DataPermission 注解）
        Mockito.verifyNoInteractions(mockProvider);
    }

    /**
     * 验证系统管理模块前缀的 Mapper 方法也返回 null（跳过数据权限过滤）
     */
    @Property(tries = 100)
    void systemMapperPrefix_getSqlSegment_returnsNull(
            @ForAll("systemMapperStatementIds") String mappedStatementId) {

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);

        Table table = new Table("sys_user");

        Expression result = handler.getSqlSegment(table, null, mappedStatementId);

        Assertions.assertThat(result).isNull();
        Mockito.verifyNoInteractions(mockProvider);
    }

    // ========== Providers ==========

    /**
     * 生成使用真实存在但未标注 @DataPermission 注解的类的 mappedStatementId。
     * 这些类都存在于 classpath 但没有数据权限注解。
     */
    @Provide
    Arbitrary<String> unannotatedClassStatementIds() {
        // 使用项目中真实存在的类，但它们不是标注了 @DataPermission 的 Mapper 接口
        Arbitrary<String> classNames = Arbitraries.of(
                "java.lang.String",
                "java.lang.Object",
                "java.util.ArrayList",
                "java.util.HashMap",
                "java.lang.Integer",
                "java.lang.Long",
                "java.util.Collections",
                "java.time.LocalDate",
                "java.time.LocalDateTime"
        );

        Arbitrary<String> methodNames = Arbitraries.of(
                "toString", "hashCode", "equals", "getClass",
                "notify", "notifyAll", "wait", "clone"
        );

        return Combinators.combine(classNames, methodNames)
                .as((cls, method) -> cls + "." + method);
    }

    /**
     * 生成系统管理模块 Mapper 前缀的 statementId
     */
    @Provide
    Arbitrary<String> systemMapperStatementIds() {
        Arbitrary<String> classNames = Arbitraries.of(
                "SysUserMapper", "SysRoleMapper", "SysOrgMapper",
                "SysMenuMapper", "SysDeptMapper", "SysConfigMapper"
        );

        Arbitrary<String> methodNames = Arbitraries.of(
                "selectList", "selectById", "insert", "update", "delete"
        );

        return Combinators.combine(classNames, methodNames)
                .as((cls, method) -> "com.zwinsight.system.mapper." + cls + "." + method);
    }

    /**
     * 生成随机表名
     */
    @Provide
    Arbitrary<String> tableNames() {
        return Arbitraries.of(
                "biz_project", "biz_inspection", "biz_contract",
                "sys_user", "sys_role", "some_table", "test_table"
        );
    }
}
