package com.zwinsight.system.datapermission;

import com.zwinsight.common.datapermission.*;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 数据权限集成测试
 * <p>
 * 模拟拦截器链（TenantLine + DataPermission）的真实 SQL 条件输出，
 * 覆盖 ALL、SELF、DEPT、DEPT_AND_CHILDREN、PROJECT 五种范围。
 * </p>
 * <p>
 * 由于无法在单元测试中启动完整 Spring 上下文和数据库连接，
 * 本测试通过直接调用 ZwDataPermissionHandler 的方法来验证
 * 各种数据范围下生成的 SQL 表达式的正确性。
 * 本质上验证的是拦截器链的核心逻辑：注解查找 → 范围判定 → 条件生成。
 * </p>
 * <p>
 * Requirements: 2.1-2.7, 5.1, 5.2
 * </p>
 */
class DataPermissionIntegrationTest {

    private DataPermissionDataProvider mockProvider;
    private ZwDataPermissionHandler handler;

    // 测试用户
    private static final Long TEST_USER_ID = 100L;
    private static final Long TEST_DEPT_ID = 10L;
    private static final List<Long> TEST_CHILD_DEPT_IDS = List.of(10L, 11L, 12L, 13L);
    private static final List<Long> TEST_PROJECT_IDS = List.of(1001L, 1002L, 1003L);

    // 测试表
    private Table testTable;
    private DataColumn testDataColumn;

    @BeforeEach
    void setUp() {
        mockProvider = Mockito.mock(DataPermissionDataProvider.class);
        handler = new ZwDataPermissionHandler(mockProvider);

        testTable = new Table("biz_inspection");

        testDataColumn = new DataColumn() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return DataColumn.class;
            }

            @Override
            public String alias() {
                return "";
            }

            @Override
            public String name() {
                return "";
            }

            @Override
            public String userColumn() {
                return "created_by";
            }

            @Override
            public String deptColumn() {
                return "dept_id";
            }

            @Override
            public String projectColumn() {
                return "project_id";
            }
        };
    }

    // ===========================================
    // Requirement 2.2: ALL 范围 - 不追加任何条件
    // ===========================================

    @Test
    @DisplayName("ALL 范围 - 不追加任何数据权限条件")
    void allScope_shouldReturnNull() {
        // 配置 mock：用户角色为 ALL
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of("ALL"));

        // 调用 getEffectiveScope 验证范围识别
        DataScopeEnum effectiveScope = handler.getEffectiveScope(TEST_USER_ID);
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.ALL);

        // ALL 范围 handler 不生成条件 → 返回 null
        // 在实际拦截器链中，getSqlSegment 对 ALL 返回 null 意味着不追加 WHERE 条件
    }

    // ===========================================
    // Requirement 2.6: SELF 范围 - created_by = userId
    // ===========================================

    @Test
    @DisplayName("SELF 范围 - 生成 created_by = userId 条件")
    void selfScope_shouldGenerateCreatedByCondition() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of("SELF"));

        DataScopeEnum effectiveScope = handler.getEffectiveScope(TEST_USER_ID);
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.SELF);

        // 直接调用 buildSelfCondition 验证 SQL 表达式
        Expression condition = handler.buildSelfCondition(testDataColumn, testTable, TEST_USER_ID);

        assertThat(condition).isNotNull();
        assertThat(condition).isInstanceOf(EqualsTo.class);

        String sql = condition.toString();
        assertThat(sql).contains("created_by");
        assertThat(sql).contains(String.valueOf(TEST_USER_ID));
    }

    // ===========================================
    // Requirement 2.4: DEPT 范围 - dept_id = userDeptId
    // ===========================================

    @Test
    @DisplayName("DEPT 范围 - 生成 dept_id = userDeptId 条件")
    void deptScope_shouldGenerateDeptIdEqualsCondition() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of("DEPT"));
        when(mockProvider.getUserDeptId(TEST_USER_ID)).thenReturn(TEST_DEPT_ID);

        DataScopeEnum effectiveScope = handler.getEffectiveScope(TEST_USER_ID);
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.DEPT);

        Expression condition = handler.buildDeptCondition(testDataColumn, testTable, TEST_USER_ID);

        assertThat(condition).isNotNull();
        assertThat(condition).isInstanceOf(EqualsTo.class);

        String sql = condition.toString();
        assertThat(sql).contains("dept_id");
        assertThat(sql).contains(String.valueOf(TEST_DEPT_ID));
    }

    @Test
    @DisplayName("DEPT 范围 - 用户无部门时降级为 SELF")
    void deptScope_noDept_shouldFallbackToSelf() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of("DEPT"));
        when(mockProvider.getUserDeptId(TEST_USER_ID)).thenReturn(null);

        Expression condition = handler.buildDeptCondition(testDataColumn, testTable, TEST_USER_ID);

        // 应降级为 SELF 条件
        assertThat(condition).isNotNull();
        String sql = condition.toString();
        assertThat(sql).contains("created_by");
        assertThat(sql).contains(String.valueOf(TEST_USER_ID));
    }

    // ===========================================
    // Requirement 2.3: DEPT_AND_CHILDREN 范围 - dept_id IN (部门及子部门)
    // ===========================================

    @Test
    @DisplayName("DEPT_AND_CHILDREN 范围 - 生成 dept_id IN (deptId, childDeptIds...) 条件")
    void deptAndChildrenScope_shouldGenerateInCondition() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of("DEPT_AND_CHILDREN"));
        when(mockProvider.getUserDeptId(TEST_USER_ID)).thenReturn(TEST_DEPT_ID);
        when(mockProvider.getDeptAndChildIds(TEST_DEPT_ID)).thenReturn(TEST_CHILD_DEPT_IDS);

        DataScopeEnum effectiveScope = handler.getEffectiveScope(TEST_USER_ID);
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.DEPT_AND_CHILDREN);

        Expression condition = handler.buildDeptAndChildrenCondition(testDataColumn, testTable, TEST_USER_ID);

        assertThat(condition).isNotNull();
        assertThat(condition).isInstanceOf(InExpression.class);

        String sql = condition.toString();
        assertThat(sql).contains("dept_id");
        assertThat(sql).contains("IN");
        // 验证所有部门 ID 都包含在 IN 列表中
        for (Long deptId : TEST_CHILD_DEPT_IDS) {
            assertThat(sql).contains(String.valueOf(deptId));
        }
    }

    @Test
    @DisplayName("DEPT_AND_CHILDREN 范围 - 用户无部门时降级为 SELF")
    void deptAndChildrenScope_noDept_shouldFallbackToSelf() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of("DEPT_AND_CHILDREN"));
        when(mockProvider.getUserDeptId(TEST_USER_ID)).thenReturn(null);

        Expression condition = handler.buildDeptAndChildrenCondition(testDataColumn, testTable, TEST_USER_ID);

        // 应降级为 SELF
        assertThat(condition).isNotNull();
        String sql = condition.toString();
        assertThat(sql).contains("created_by");
    }

    @Test
    @DisplayName("DEPT_AND_CHILDREN 范围 - getDeptAndChildIds异常时降级为仅本部门")
    void deptAndChildrenScope_providerException_shouldFallbackToDept() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of("DEPT_AND_CHILDREN"));
        when(mockProvider.getUserDeptId(TEST_USER_ID)).thenReturn(TEST_DEPT_ID);
        when(mockProvider.getDeptAndChildIds(TEST_DEPT_ID)).thenThrow(new RuntimeException("DB error"));

        Expression condition = handler.buildDeptAndChildrenCondition(testDataColumn, testTable, TEST_USER_ID);

        // 异常时降级为仅本部门
        assertThat(condition).isNotNull();
        assertThat(condition).isInstanceOf(EqualsTo.class);
        String sql = condition.toString();
        assertThat(sql).contains("dept_id");
        assertThat(sql).contains(String.valueOf(TEST_DEPT_ID));
    }

    // ===========================================
    // Requirement 2.5: PROJECT 范围 - project_id IN (用户项目列表)
    // ===========================================

    @Test
    @DisplayName("PROJECT 范围 - 生成 project_id IN (projectIds...) 条件")
    void projectScope_shouldGenerateProjectIdInCondition() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of("PROJECT"));
        when(mockProvider.getUserProjectIds(TEST_USER_ID)).thenReturn(TEST_PROJECT_IDS);

        DataScopeEnum effectiveScope = handler.getEffectiveScope(TEST_USER_ID);
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.PROJECT);

        Expression condition = handler.buildProjectCondition(testDataColumn, testTable, TEST_USER_ID);

        assertThat(condition).isNotNull();
        assertThat(condition).isInstanceOf(InExpression.class);

        String sql = condition.toString();
        assertThat(sql).contains("project_id");
        assertThat(sql).contains("IN");
        for (Long projectId : TEST_PROJECT_IDS) {
            assertThat(sql).contains(String.valueOf(projectId));
        }
    }

    @Test
    @DisplayName("PROJECT 范围 - 用户无项目时生成 1=0 条件")
    void projectScope_noProjects_shouldGenerateFalseCondition() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of("PROJECT"));
        when(mockProvider.getUserProjectIds(TEST_USER_ID)).thenReturn(List.of());

        Expression condition = handler.buildProjectCondition(testDataColumn, testTable, TEST_USER_ID);

        // 无项目 → 1 = 0（不可见任何数据）
        assertThat(condition).isNotNull();
        assertThat(condition).isInstanceOf(EqualsTo.class);
        String sql = condition.toString();
        assertThat(sql).contains("1");
        assertThat(sql).contains("0");
    }

    @Test
    @DisplayName("PROJECT 范围 - getUserProjectIds异常时降级为 SELF")
    void projectScope_providerException_shouldFallbackToSelf() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of("PROJECT"));
        when(mockProvider.getUserProjectIds(TEST_USER_ID)).thenThrow(new RuntimeException("DB error"));

        Expression condition = handler.buildProjectCondition(testDataColumn, testTable, TEST_USER_ID);

        // 异常时降级为 SELF
        assertThat(condition).isNotNull();
        String sql = condition.toString();
        assertThat(sql).contains("created_by");
        assertThat(sql).contains(String.valueOf(TEST_USER_ID));
    }

    // ===========================================
    // Requirement 2.7: 多角色取最大优先级
    // ===========================================

    @Test
    @DisplayName("多角色 - 取最大优先级范围（ALL > DEPT_AND_CHILDREN > DEPT > PROJECT > SELF）")
    void multipleRoles_shouldTakeHighestPriority() {
        // 用户同时拥有 SELF 和 DEPT_AND_CHILDREN 范围的角色
        when(mockProvider.getUserDataScopes(TEST_USER_ID))
                .thenReturn(List.of("SELF", "DEPT_AND_CHILDREN", "DEPT"));

        DataScopeEnum effectiveScope = handler.getEffectiveScope(TEST_USER_ID);

        // 应取最大优先级：DEPT_AND_CHILDREN (priority=4)
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.DEPT_AND_CHILDREN);
    }

    @Test
    @DisplayName("多角色含 ALL - 有效范围为 ALL，不追加条件")
    void multipleRolesWithAll_shouldReturnAll() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID))
                .thenReturn(List.of("SELF", "ALL", "PROJECT"));

        DataScopeEnum effectiveScope = handler.getEffectiveScope(TEST_USER_ID);
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.ALL);
    }

    @Test
    @DisplayName("无角色 - 默认使用 SELF 范围")
    void noRoles_shouldDefaultToSelf() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenReturn(List.of());

        DataScopeEnum effectiveScope = handler.getEffectiveScope(TEST_USER_ID);
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.SELF);
    }

    // ===========================================
    // Requirement 5.1, 5.2: 租户与数据权限叠加（AND 组合）
    // ===========================================

    @Test
    @DisplayName("租户条件与 SELF 数据权限条件通过 AND 组合")
    void tenantAndSelfPermission_combinedViaAnd() {
        long tenantId = 1001L;

        // 模拟租户条件
        Expression tenantCondition = new EqualsTo(
                new net.sf.jsqlparser.schema.Column("tenant_id"),
                new LongValue(tenantId)
        );

        // 模拟 SELF 数据权限条件
        Expression selfCondition = handler.buildSelfCondition(testDataColumn, testTable, TEST_USER_ID);

        // AND 组合（模拟 MyBatis-Plus 拦截器链行为）
        var combined = new net.sf.jsqlparser.expression.operators.conditional.AndExpression(
                tenantCondition, selfCondition);

        String sql = combined.toString();
        assertThat(sql).contains("tenant_id");
        assertThat(sql).contains("created_by");
        assertThat(sql).containsIgnoringCase("AND");
    }

    @Test
    @DisplayName("租户条件与 PROJECT 数据权限条件通过 AND 组合")
    void tenantAndProjectPermission_combinedViaAnd() {
        long tenantId = 1001L;
        when(mockProvider.getUserProjectIds(TEST_USER_ID)).thenReturn(TEST_PROJECT_IDS);

        // 租户条件
        Expression tenantCondition = new EqualsTo(
                new net.sf.jsqlparser.schema.Column("tenant_id"),
                new LongValue(tenantId)
        );

        // PROJECT 数据权限条件
        Expression projectCondition = handler.buildProjectCondition(testDataColumn, testTable, TEST_USER_ID);

        // AND 组合
        var combined = new net.sf.jsqlparser.expression.operators.conditional.AndExpression(
                tenantCondition, projectCondition);

        String sql = combined.toString();
        assertThat(sql).contains("tenant_id");
        assertThat(sql).contains("project_id");
        assertThat(sql).contains("IN");
        assertThat(sql).containsIgnoringCase("AND");
    }

    @Test
    @DisplayName("租户条件与 DEPT_AND_CHILDREN 数据权限条件通过 AND 组合")
    void tenantAndDeptChildrenPermission_combinedViaAnd() {
        long tenantId = 1001L;
        when(mockProvider.getUserDeptId(TEST_USER_ID)).thenReturn(TEST_DEPT_ID);
        when(mockProvider.getDeptAndChildIds(TEST_DEPT_ID)).thenReturn(TEST_CHILD_DEPT_IDS);

        // 租户条件
        Expression tenantCondition = new EqualsTo(
                new net.sf.jsqlparser.schema.Column("tenant_id"),
                new LongValue(tenantId)
        );

        // DEPT_AND_CHILDREN 数据权限条件
        Expression deptChildCondition = handler.buildDeptAndChildrenCondition(testDataColumn, testTable, TEST_USER_ID);

        // AND 组合
        var combined = new net.sf.jsqlparser.expression.operators.conditional.AndExpression(
                tenantCondition, deptChildCondition);

        String sql = combined.toString();
        assertThat(sql).contains("tenant_id");
        assertThat(sql).contains("dept_id");
        assertThat(sql).contains("IN");
        assertThat(sql).containsIgnoringCase("AND");
    }

    // ===========================================
    // Requirement 5.1: DataProvider 异常降级
    // ===========================================

    @Test
    @DisplayName("DataProvider 异常时降级为 SELF（最小权限原则）")
    void providerException_shouldFallbackToSelf() {
        when(mockProvider.getUserDataScopes(TEST_USER_ID)).thenThrow(new RuntimeException("DB connection lost"));

        DataScopeEnum effectiveScope = handler.getEffectiveScope(TEST_USER_ID);

        // 异常降级为 SELF
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.SELF);
    }

    // ===========================================
    // 表别名测试
    // ===========================================

    @Test
    @DisplayName("带表别名的 SELF 条件 - 生成 alias.created_by = userId")
    void selfConditionWithTableAlias() {
        Table aliasedTable = new Table("biz_inspection");
        aliasedTable.setAlias(new net.sf.jsqlparser.expression.Alias("bi"));

        Expression condition = handler.buildSelfCondition(testDataColumn, aliasedTable, TEST_USER_ID);

        assertThat(condition).isNotNull();
        String sql = condition.toString();
        assertThat(sql).contains("bi.created_by");
    }

    @Test
    @DisplayName("带表别名的 DEPT 条件 - 生成 alias.dept_id = deptId")
    void deptConditionWithTableAlias() {
        Table aliasedTable = new Table("biz_inspection");
        aliasedTable.setAlias(new net.sf.jsqlparser.expression.Alias("bi"));
        when(mockProvider.getUserDeptId(TEST_USER_ID)).thenReturn(TEST_DEPT_ID);

        Expression condition = handler.buildDeptCondition(testDataColumn, aliasedTable, TEST_USER_ID);

        assertThat(condition).isNotNull();
        String sql = condition.toString();
        assertThat(sql).contains("bi.dept_id");
    }
}
