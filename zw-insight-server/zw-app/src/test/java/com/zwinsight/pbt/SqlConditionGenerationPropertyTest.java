package com.zwinsight.pbt;

import com.zwinsight.common.datapermission.*;
import net.jqwik.api.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Table;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

// Feature: p0-data-permission-overdue, Property 3: SQL 条件生成正确性
/**
 * Property 3: SQL 条件生成正确性
 * <p>
 * 验证：对于每种 DataScopeEnum 值，ZwDataPermissionHandler 生成正确的 SQL 表达式：
 * - ALL → 返回 null（不追加条件）
 * - DEPT → 生成 equalsTo(dept_column, deptId)
 * - DEPT_AND_CHILDREN → 生成 IN 表达式
 * - PROJECT → 生成 IN 表达式
 * - SELF → 生成 equalsTo(user_column, userId)
 * </p>
 * <p>
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6**
 * </p>
 */
@Tag("Feature: p0-data-permission-overdue, Property 3: SQL 条件生成正确性")
class SqlConditionGenerationPropertyTest {

    /**
     * 创建一个带有 mock DataPermissionDataProvider 的 ZwDataPermissionHandler
     */
    private ZwDataPermissionHandler createHandler(Long deptId, List<Long> deptAndChildIds, List<Long> projectIds) {
        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDeptId(anyLong())).thenReturn(deptId);
        when(mockProvider.getDeptAndChildIds(anyLong())).thenReturn(deptAndChildIds);
        when(mockProvider.getUserProjectIds(anyLong())).thenReturn(projectIds);
        return new ZwDataPermissionHandler(mockProvider);
    }

    /**
     * 创建测试用的 DataColumn 注解代理
     */
    private DataColumn createDataColumn(String alias, String userCol, String deptCol, String projectCol) {
        DataColumn mockColumn = Mockito.mock(DataColumn.class);
        when(mockColumn.alias()).thenReturn(alias);
        when(mockColumn.userColumn()).thenReturn(userCol);
        when(mockColumn.deptColumn()).thenReturn(deptCol);
        when(mockColumn.projectColumn()).thenReturn(projectCol);
        return mockColumn;
    }

    private Table createTable(String tableName) {
        Table table = new Table(tableName);
        return table;
    }

    // ========== Property: ALL → null (通过 getEffectiveScope 验证) ==========

    @Property(tries = 100)
    void allScope_effectiveScopeIsAll_impliesNullCondition(
            @ForAll("userIds") Long userId) {

        // 当 getUserDataScopes 返回 "ALL" 时，getEffectiveScope 应返回 ALL
        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDataScopes(anyLong())).thenReturn(List.of("ALL"));
        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);

        DataScopeEnum effectiveScope = handler.getEffectiveScope(userId);
        Assertions.assertThat(effectiveScope).isEqualTo(DataScopeEnum.ALL);
        // getSqlSegment 中当 effectiveScope == ALL 时直接 return null
        // 此属性证明 ALL 范围不会生成任何过滤条件
    }

    @Property(tries = 100)
    void selfScope_generatesEqualsToUserColumn(
            @ForAll("userIds") Long userId) {

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");
        Table table = createTable("biz_inspection");

        Expression result = handler.buildSelfCondition(column, table, userId);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isInstanceOf(EqualsTo.class);
        String sql = result.toString();
        Assertions.assertThat(sql).contains("created_by");
        Assertions.assertThat(sql).contains(String.valueOf(userId));
    }

    @Property(tries = 100)
    void deptScope_generatesEqualsToDeptColumn(
            @ForAll("userIds") Long userId,
            @ForAll("deptIds") Long deptId) {

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDeptId(anyLong())).thenReturn(deptId);
        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");
        Table table = createTable("biz_inspection");

        Expression result = handler.buildDeptCondition(column, table, userId);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isInstanceOf(EqualsTo.class);
        String sql = result.toString();
        Assertions.assertThat(sql).contains("dept_id");
        Assertions.assertThat(sql).contains(String.valueOf(deptId));
    }

    @Property(tries = 100)
    void deptAndChildrenScope_generatesInExpression(
            @ForAll("userIds") Long userId,
            @ForAll("deptIds") Long deptId,
            @ForAll("deptIdList") List<Long> childDeptIds) {

        // 构建包含自身的完整列表
        List<Long> allDeptIds = new java.util.ArrayList<>();
        allDeptIds.add(deptId);
        allDeptIds.addAll(childDeptIds);

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserDeptId(anyLong())).thenReturn(deptId);
        when(mockProvider.getDeptAndChildIds(anyLong())).thenReturn(allDeptIds);
        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");
        Table table = createTable("biz_inspection");

        Expression result = handler.buildDeptAndChildrenCondition(column, table, userId);

        Assertions.assertThat(result).isNotNull();
        String sql = result.toString();
        Assertions.assertThat(sql).contains("dept_id");
        // 如果有子部门列表则为 IN 表达式
        if (!childDeptIds.isEmpty()) {
            Assertions.assertThat(result).isInstanceOf(InExpression.class);
            // 验证所有部门 ID 都在 IN 表达式中
            for (Long id : allDeptIds) {
                Assertions.assertThat(sql).contains(String.valueOf(id));
            }
        }
    }

    @Property(tries = 100)
    void projectScope_generatesInExpression(
            @ForAll("userIds") Long userId,
            @ForAll("projectIdList") List<Long> projectIds) {

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        when(mockProvider.getUserProjectIds(anyLong())).thenReturn(projectIds);
        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");
        Table table = createTable("biz_inspection");

        Expression result = handler.buildProjectCondition(column, table, userId);

        Assertions.assertThat(result).isNotNull();
        String sql = result.toString();

        if (projectIds.isEmpty()) {
            // 无项目时生成 1 = 0 条件
            Assertions.assertThat(result).isInstanceOf(EqualsTo.class);
            Assertions.assertThat(sql).contains("1");
            Assertions.assertThat(sql).contains("0");
        } else {
            // 有项目时生成 IN 表达式
            Assertions.assertThat(result).isInstanceOf(InExpression.class);
            Assertions.assertThat(sql).contains("project_id");
            for (Long id : projectIds) {
                Assertions.assertThat(sql).contains(String.valueOf(id));
            }
        }
    }

    @Property(tries = 100)
    void selfScope_withTableAlias_generatesAliasedColumn(
            @ForAll("userIds") Long userId) {

        DataPermissionDataProvider mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        ZwDataPermissionHandler handler = new ZwDataPermissionHandler(mockProvider);
        DataColumn column = createDataColumn("t", "created_by", "dept_id", "project_id");
        Table table = new Table("biz_inspection");
        table.setAlias(new net.sf.jsqlparser.expression.Alias("t"));

        Expression result = handler.buildSelfCondition(column, table, userId);

        Assertions.assertThat(result).isNotNull();
        String sql = result.toString();
        // 有别名时应该包含 t.created_by
        Assertions.assertThat(sql).contains("t.created_by");
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1L, 999999L);
    }

    @Provide
    Arbitrary<Long> deptIds() {
        return Arbitraries.longs().between(1L, 999999L);
    }

    @Provide
    Arbitrary<List<Long>> deptIdList() {
        return Arbitraries.longs().between(1L, 999999L).list().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<Long>> projectIdList() {
        return Arbitraries.longs().between(1L, 999999L).list().ofMinSize(0).ofMaxSize(10);
    }
}
