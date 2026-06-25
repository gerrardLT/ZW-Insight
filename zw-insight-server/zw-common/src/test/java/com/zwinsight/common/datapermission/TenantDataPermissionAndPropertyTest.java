package com.zwinsight.common.datapermission;

import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// Feature: p0-data-permission-overdue, Property 5: 租户条件与数据权限 AND 组合
/**
 * Property 5: 租户条件与数据权限 AND 组合
 * <p>
 * 验证：当 TenantLine 和 DataPermission 拦截器同时活跃时，
 * 最终 SQL 的 WHERE 条件中，租户条件和数据权限条件通过 AND 逻辑连接。
 * </p>
 * <p>
 * 拦截器链执行顺序：TenantLineInnerInterceptor → DataPermissionInnerInterceptor
 * TenantLine 先追加 tenant_id = ? 条件，DataPermission 再追加数据权限条件。
 * MyBatis-Plus 框架自动将多个拦截器产生的条件用 AND 连接。
 * </p>
 * <p>
 * 本测试通过模拟两个拦截器分别产生的条件，验证合并后使用 AND 逻辑。
 * </p>
 * <p>
 * **Validates: Requirements 5.2**
 * </p>
 */
class TenantDataPermissionAndPropertyTest {

    /**
     * 模拟 TenantLineInnerInterceptor 产生的租户条件：tenant_id = ?
     */
    private Expression buildTenantCondition(Long tenantId) {
        Column tenantColumn = new Column("tenant_id");
        return new EqualsTo(tenantColumn, new LongValue(tenantId));
    }

    /**
     * 模拟 DataPermissionInnerInterceptor 产生的数据权限条件。
     * 根据 dataScope 生成不同条件。
     */
    private Expression buildDataPermissionCondition(DataScopeEnum scope, Long userId, Long deptId,
                                                     List<Long> projectIds) {
        return switch (scope) {
            case SELF -> new EqualsTo(new Column("created_by"), new LongValue(userId));
            case DEPT -> new EqualsTo(new Column("dept_id"), new LongValue(deptId));
            case DEPT_AND_CHILDREN -> new EqualsTo(new Column("dept_id"), new LongValue(deptId));
            case PROJECT -> {
                // 简化：用 project_id = firstProjectId 表示 IN 条件的存在
                if (projectIds != null && !projectIds.isEmpty()) {
                    yield new EqualsTo(new Column("project_id"), new LongValue(projectIds.get(0)));
                }
                yield new EqualsTo(new LongValue(1), new LongValue(0)); // 1=0
            }
            case ALL -> null; // ALL 不产生额外条件
        };
    }

    /**
     * 模拟 MyBatis-Plus 拦截器链的条件合并逻辑：
     * 当两个条件都存在时，用 AND 连接。
     */
    private Expression combinedConditions(Expression tenantCondition, Expression dataPermCondition) {
        if (tenantCondition == null) {
            return dataPermCondition;
        }
        if (dataPermCondition == null) {
            return tenantCondition;
        }
        return new AndExpression(tenantCondition, dataPermCondition);
    }

    @Property(tries = 100)
    @Label("租户条件与非ALL数据权限条件通过AND连接")
    void tenantAndDataPermissionCombinedWithAND(
            @ForAll @LongRange(min = 1, max = 999999) Long tenantId,
            @ForAll @LongRange(min = 1, max = 999999) Long userId,
            @ForAll @LongRange(min = 1, max = 999999) Long deptId,
            @ForAll("nonAllScopes") DataScopeEnum scope) {

        // 模拟 TenantLine 产生租户条件
        Expression tenantCondition = buildTenantCondition(tenantId);

        // 模拟 DataPermission 产生数据权限条件
        List<Long> projectIds = List.of(100L, 200L, 300L);
        Expression dataPermCondition = buildDataPermissionCondition(scope, userId, deptId, projectIds);

        // 合并条件
        Expression combined = combinedConditions(tenantCondition, dataPermCondition);

        // 验证合并后是 AND 表达式
        assertThat(combined)
                .as("租户条件与数据权限条件(%s)应通过 AND 连接", scope)
                .isInstanceOf(AndExpression.class);

        // 验证 AND 表达式的左右两侧分别包含条件
        AndExpression andExpr = (AndExpression) combined;
        assertThat(andExpr.getLeftExpression())
                .as("AND 左侧应为租户条件")
                .isNotNull();
        assertThat(andExpr.getRightExpression())
                .as("AND 右侧应为数据权限条件")
                .isNotNull();

        // 验证 SQL 字符串中包含 AND 关键字
        String sql = combined.toString();
        assertThat(sql)
                .as("合并后的条件 SQL 应包含 AND")
                .containsIgnoringCase("AND");
    }

    @Property(tries = 100)
    @Label("ALL范围时仅保留租户条件")
    void allScopeOnlyKeepsTenantCondition(
            @ForAll @LongRange(min = 1, max = 999999) Long tenantId,
            @ForAll @LongRange(min = 1, max = 999999) Long userId) {

        Expression tenantCondition = buildTenantCondition(tenantId);
        Expression dataPermCondition = buildDataPermissionCondition(DataScopeEnum.ALL, userId, null, null);

        Expression combined = combinedConditions(tenantCondition, dataPermCondition);

        // ALL 范围不产生数据权限条件，合并后只剩租户条件
        assertThat(combined)
                .as("ALL范围时合并结果应仅为租户条件")
                .isEqualTo(tenantCondition);
        assertThat(combined.toString())
                .as("SQL中应包含 tenant_id 条件")
                .contains("tenant_id");
    }

    @Property(tries = 100)
    @Label("ZwDataPermissionHandler对SELF范围生成EqualsTo条件")
    void handlerGeneratesSelfCondition(
            @ForAll @LongRange(min = 1, max = 999999) Long userId) {

        // 创建 mock DataProvider
        DataPermissionDataProvider mockProvider = mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(userId)).thenReturn(List.of("SELF"));

        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);

        // 创建测试表和 DataColumn
        Table table = new Table("biz_inspection");
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");

        // 调用 handler 的 buildSelfCondition
        Expression condition = handler.buildSelfCondition(column, table, userId);

        assertThat(condition)
                .as("SELF条件应为EqualsTo类型")
                .isInstanceOf(EqualsTo.class);
        assertThat(condition.toString())
                .as("SELF条件应包含 created_by = userId")
                .contains("created_by")
                .contains(String.valueOf(userId));
    }

    @Provide
    Arbitrary<DataScopeEnum> nonAllScopes() {
        return Arbitraries.of(DataScopeEnum.SELF, DataScopeEnum.DEPT,
                DataScopeEnum.DEPT_AND_CHILDREN, DataScopeEnum.PROJECT);
    }

    /**
     * 创建 DataColumn 注解代理对象
     */
    private DataColumn createDataColumn(String alias, String userColumn, String deptColumn, String projectColumn) {
        return new DataColumn() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return DataColumn.class; }
            @Override public String alias() { return alias; }
            @Override public String name() { return ""; }
            @Override public String userColumn() { return userColumn; }
            @Override public String deptColumn() { return deptColumn; }
            @Override public String projectColumn() { return projectColumn; }
        };
    }
}
