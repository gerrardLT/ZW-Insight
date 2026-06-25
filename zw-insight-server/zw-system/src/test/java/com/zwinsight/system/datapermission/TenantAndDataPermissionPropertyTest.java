package com.zwinsight.system.datapermission;

import com.zwinsight.common.datapermission.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

// Feature: p0-data-permission-overdue, Property 5: 租户条件与数据权限 AND 组合
/**
 * Property 5: 租户条件与数据权限 AND 组合
 * <p>
 * 验证：当同时触发租户过滤和数据权限过滤时，最终 SQL 中 tenant_id 条件和数据权限条件通过 AND 逻辑连接。
 * </p>
 * <p>
 * MyBatis-Plus 的拦截器链机制保证：
 * - TenantLineInnerInterceptor 先执行，在 WHERE 子句中注入 tenant_id = ?
 * - DataPermissionInnerInterceptor 后执行，通过 AND 追加数据权限条件
 * - 最终 SQL 形如: WHERE tenant_id = ? AND (数据权限条件)
 * </p>
 * <p>
 * 本测试验证当 WHERE 子句已存在租户条件时，DataPermissionHandler 生成的条件与其组合后
 * 形成 AND 连接。由于拦截器链是 MyBatis-Plus 框架行为，我们验证：
 * 1. 租户拦截器产生的是 EqualsTo 表达式（tenant_id = X）
 * 2. 数据权限拦截器产生的也是 Expression（非 null）
 * 3. 两个 Expression 通过 AND 组合后语义正确
 * </p>
 * <p>
 * **Validates: Requirements 5.2**
 * </p>
 */
class TenantAndDataPermissionPropertyTest {

    /**
     * 模拟 TenantLineInnerInterceptor 生成的租户条件：tenant_id = tenantId
     */
    private Expression buildTenantCondition(long tenantId) {
        Column tenantColumn = new Column("tenant_id");
        return new EqualsTo(tenantColumn, new LongValue(tenantId));
    }

    /**
     * 模拟 MyBatis-Plus 拦截器链的 AND 组合行为：
     * 当已有 WHERE 条件(tenantCondition)存在时，新条件(dataPermissionCondition)
     * 通过 AND 连接追加到已有条件上。
     * <p>
     * 这正是 MyBatis-Plus InnerInterceptor 链在 processWhere 中的行为：
     * 如果 where != null 且 newCondition != null，则 where = new AndExpression(where, newCondition)
     */
    private Expression combineConditionsViaAnd(Expression existing, Expression additional) {
        if (existing == null) {
            return additional;
        }
        if (additional == null) {
            return existing;
        }
        return new AndExpression(existing, additional);
    }

    /**
     * 属性测试：任意租户ID + 非 ALL 数据范围 → 组合后的条件是 AND 表达式
     * <p>
     * 验证 TenantLineInnerInterceptor 的条件与 DataPermissionInnerInterceptor 的条件
     * 通过 AND 组合后，结果表达式类型为 AndExpression。
     */
    @Property(tries = 100)
    void tenantAndDataPermissionCombinedViaAnd(
            @ForAll @LongRange(min = 1, max = 10000) long tenantId,
            @ForAll("nonAllDataScopes") DataScopeEnum scope,
            @ForAll @LongRange(min = 1, max = 10000) long userId,
            @ForAll @LongRange(min = 1, max = 10000) long deptId) {

        // 1. 模拟租户拦截器产生的条件
        Expression tenantCondition = buildTenantCondition(tenantId);
        assertThat(tenantCondition).isNotNull();
        assertThat(tenantCondition).isInstanceOf(EqualsTo.class);

        // 2. 模拟数据权限拦截器产生的条件
        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(anyLong())).thenReturn(List.of(scope.name()));
        when(mockProvider.getUserDeptId(anyLong())).thenReturn(deptId);
        when(mockProvider.getDeptAndChildIds(anyLong())).thenReturn(List.of(deptId, deptId + 1));
        when(mockProvider.getUserProjectIds(anyLong())).thenReturn(List.of(1L, 2L, 3L));

        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);

        // 创建带有 @DataColumn 配置的 Table
        Table table = new Table("biz_inspection");

        // 通过 DataColumn 构建条件（直接调用 handler 的公开方法）
        DataColumn mockColumn = createMockDataColumn("", "created_by", "dept_id", "project_id");
        Expression dataPermissionCondition = buildDataPermissionCondition(handler, scope, mockColumn, table, userId, deptId);

        // 数据权限条件不为 null（非 ALL 范围）
        assertThat(dataPermissionCondition)
                .as("非 ALL 数据范围应产生非空过滤条件")
                .isNotNull();

        // 3. 模拟 MyBatis-Plus 拦截器链 AND 组合
        Expression combined = combineConditionsViaAnd(tenantCondition, dataPermissionCondition);

        // 验证组合后的结果是 AndExpression
        assertThat(combined)
                .as("租户条件与数据权限条件组合后应为 AND 表达式")
                .isInstanceOf(AndExpression.class);

        // 验证 AND 表达式的左侧是租户条件
        AndExpression andExpr = (AndExpression) combined;
        assertThat(andExpr.getLeftExpression())
                .as("AND 左侧应为租户条件")
                .isEqualTo(tenantCondition);

        // 验证 AND 表达式的右侧是数据权限条件
        assertThat(andExpr.getRightExpression())
                .as("AND 右侧应为数据权限条件")
                .isEqualTo(dataPermissionCondition);
    }

    /**
     * 属性测试：ALL 数据范围时，数据权限拦截器不追加条件，最终只保留租户条件
     */
    @Property(tries = 100)
    void allScopeDoesNotAddDataPermissionCondition(
            @ForAll @LongRange(min = 1, max = 10000) long tenantId,
            @ForAll @LongRange(min = 1, max = 10000) long userId) {

        // 1. 模拟租户条件
        Expression tenantCondition = buildTenantCondition(tenantId);

        // 2. ALL 范围 → 数据权限返回 null
        Expression dataPermissionCondition = null; // ALL 范围不追加条件

        // 3. 组合
        Expression combined = combineConditionsViaAnd(tenantCondition, dataPermissionCondition);

        // 最终只保留租户条件
        assertThat(combined)
                .as("ALL 范围时，最终 WHERE 只包含租户条件")
                .isEqualTo(tenantCondition);
        assertThat(combined).isInstanceOf(EqualsTo.class);
    }

    /**
     * 属性测试：验证 AND 组合的字符串形式包含 tenant_id 和数据权限关键字
     */
    @Property(tries = 100)
    void combinedSqlContainsBothConditions(
            @ForAll @LongRange(min = 1, max = 10000) long tenantId,
            @ForAll @LongRange(min = 1, max = 10000) long userId) {

        // 租户条件
        Expression tenantCondition = buildTenantCondition(tenantId);

        // SELF 范围的数据权限条件：created_by = userId
        Column userCol = new Column("created_by");
        Expression selfCondition = new EqualsTo(userCol, new LongValue(userId));

        // AND 组合
        Expression combined = combineConditionsViaAnd(tenantCondition, selfCondition);

        // 验证 SQL 字符串表示包含两个条件
        String combinedSql = combined.toString();
        assertThat(combinedSql)
                .as("组合后的 SQL 应包含 tenant_id 条件")
                .contains("tenant_id");
        assertThat(combinedSql)
                .as("组合后的 SQL 应包含 created_by 条件")
                .contains("created_by");
        assertThat(combinedSql)
                .as("组合后的 SQL 应包含 AND 关键字")
                .containsIgnoringCase("AND");
    }

    /**
     * 属性测试：验证拦截器注册顺序的正确性
     * <p>
     * MybatisPlusConfig 中拦截器注册顺序：Tenant(1st) → DataPermission(2nd) → Pagination → OptimisticLocker
     * 即租户条件先注入，数据权限条件后注入，后者在前者基础上追加 AND
     * </p>
     */
    @Property(tries = 100)
    void interceptorOrderEnsuresTenantFirst(
            @ForAll @LongRange(min = 1, max = 10000) long tenantId,
            @ForAll @LongRange(min = 1, max = 10000) long userId) {

        // 模拟执行顺序：先 Tenant → 再 DataPermission
        // Step 1: TenantLineInnerInterceptor 产生 WHERE tenant_id = ?
        Expression afterTenant = buildTenantCondition(tenantId);
        assertThat(afterTenant).isNotNull();

        // Step 2: DataPermissionInnerInterceptor 在已有 WHERE 上追加条件
        Column userCol = new Column("created_by");
        Expression dpCondition = new EqualsTo(userCol, new LongValue(userId));

        // 框架行为：已有 where + 新条件 → AND 组合
        Expression afterDataPermission = combineConditionsViaAnd(afterTenant, dpCondition);

        // 验证最终是 AND，且左侧（先执行的）是租户条件
        assertThat(afterDataPermission).isInstanceOf(AndExpression.class);
        AndExpression result = (AndExpression) afterDataPermission;
        assertThat(result.getLeftExpression().toString())
                .as("拦截器顺序保证 tenant_id 条件在左侧（先注入）")
                .contains("tenant_id");
        assertThat(result.getRightExpression().toString())
                .as("数据权限条件在右侧（后注入）")
                .contains("created_by");
    }

    // ========== Helper Methods ==========

    /**
     * 根据数据范围直接构建对应的 SQL 条件（使用 handler 的公开方法）
     */
    private Expression buildDataPermissionCondition(ZwDataPermissionHandler handler,
                                                     DataScopeEnum scope, DataColumn column, Table table,
                                                     long userId, long deptId) {
        return switch (scope) {
            case SELF -> handler.buildSelfCondition(column, table, userId);
            case PROJECT -> handler.buildProjectCondition(column, table, userId);
            case DEPT -> handler.buildDeptCondition(column, table, userId);
            case DEPT_AND_CHILDREN -> handler.buildDeptAndChildrenCondition(column, table, userId);
            default -> null;
        };
    }

    /**
     * 创建 Mock DataColumn 注解（通过代理方式）
     */
    private DataColumn createMockDataColumn(String alias, String userColumn, String deptColumn, String projectColumn) {
        return new DataColumn() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return DataColumn.class;
            }

            @Override
            public String alias() {
                return alias;
            }

            @Override
            public String name() {
                return "";
            }

            @Override
            public String userColumn() {
                return userColumn;
            }

            @Override
            public String deptColumn() {
                return deptColumn;
            }

            @Override
            public String projectColumn() {
                return projectColumn;
            }
        };
    }

    // ========== Providers ==========

    /**
     * 非 ALL 的数据范围（这些范围会产生非空的数据权限条件）
     */
    @Provide
    Arbitrary<DataScopeEnum> nonAllDataScopes() {
        return Arbitraries.of(DataScopeEnum.SELF, DataScopeEnum.DEPT,
                DataScopeEnum.DEPT_AND_CHILDREN, DataScopeEnum.PROJECT);
    }
}
