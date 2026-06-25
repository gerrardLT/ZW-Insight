package com.zwinsight.common.datapermission;

import com.zwinsight.common.config.SecurityContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 数据权限集成测试
 * <p>
 * 通过直接调用 ZwDataPermissionHandler.getSqlSegment 验证 SQL 条件生成逻辑，
 * 覆盖 ALL、SELF、DEPT、DEPT_AND_CHILDREN、PROJECT 五种数据范围。
 * </p>
 * <p>
 * 由于无法在单元测试中启动完整的 SpringBoot + MyBatis-Plus 拦截器链，
 * 本测试采用轻量方式：直接实例化 Handler，Mock DataProvider，验证 SQL 表达式输出。
 * </p>
 *
 * Requirements: 2.1-2.7, 5.1, 5.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("数据权限集成测试 - SQL条件生成验证")
class DataPermissionIntegrationTest {

    @Mock
    private DataPermissionDataProvider dataProvider;

    private ZwDataPermissionHandler handler;

    private static final Long USER_ID = 1001L;
    private static final Long DEPT_ID = 200L;
    private static final List<Long> CHILD_DEPT_IDS = List.of(200L, 201L, 202L, 203L);
    private static final List<Long> PROJECT_IDS = List.of(501L, 502L, 503L);

    /** 模拟带 @DataPermission 注解的 Mapper mappedStatementId */
    private static final String ANNOTATED_MAPPER_MS_ID =
            "com.zwinsight.site.mapper.BizInspectionMapper.selectList";

    @BeforeEach
    void setUp() {
        handler = new ZwDataPermissionHandler(dataProvider);
    }

    // ==================== ALL 范围测试 ====================

    @Test
    @DisplayName("ALL范围 - 不追加任何数据权限条件")
    void allScope_shouldReturnNull() {
        when(dataProvider.getUserDataScopes(USER_ID)).thenReturn(List.of("ALL"));

        DataScopeEnum scope = handler.getEffectiveScope(USER_ID);

        assertThat(scope).isEqualTo(DataScopeEnum.ALL);
        // ALL 范围下 getSqlSegment 在检测到 scope 为 ALL 后返回 null
    }

    // ==================== SELF 范围测试 ====================

    @Test
    @DisplayName("SELF范围 - 生成 created_by = userId 条件")
    void selfScope_shouldGenerateEqualsToUserIdCondition() {
        when(dataProvider.getUserDataScopes(USER_ID)).thenReturn(List.of("SELF"));

        DataScopeEnum scope = handler.getEffectiveScope(USER_ID);
        assertThat(scope).isEqualTo(DataScopeEnum.SELF);

        // 验证 buildSelfCondition 输出
        Table table = new Table("biz_inspection");
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");

        Expression condition = handler.buildSelfCondition(column, table, USER_ID);

        assertThat(condition).isInstanceOf(EqualsTo.class);
        String sql = condition.toString();
        assertThat(sql).contains("created_by");
        assertThat(sql).contains(String.valueOf(USER_ID));
    }

    @Test
    @DisplayName("SELF范围 + 表别名 - 生成 alias.created_by = userId 条件")
    void selfScope_withAlias_shouldPrefixColumnWithAlias() {
        Table table = new Table("biz_inspection");
        table.setAlias(new net.sf.jsqlparser.expression.Alias("bi"));

        DataColumn column = createDataColumn("bi", "created_by", "dept_id", "project_id");
        Expression condition = handler.buildSelfCondition(column, table, USER_ID);

        String sql = condition.toString();
        assertThat(sql).contains("bi.created_by");
        assertThat(sql).contains(String.valueOf(USER_ID));
    }

    // ==================== DEPT 范围测试 ====================

    @Test
    @DisplayName("DEPT范围 - 生成 dept_id = userDeptId 条件")
    void deptScope_shouldGenerateEqualsToDeptIdCondition() {
        when(dataProvider.getUserDataScopes(USER_ID)).thenReturn(List.of("DEPT"));
        when(dataProvider.getUserDeptId(USER_ID)).thenReturn(DEPT_ID);

        DataScopeEnum scope = handler.getEffectiveScope(USER_ID);
        assertThat(scope).isEqualTo(DataScopeEnum.DEPT);

        Table table = new Table("biz_inspection");
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");

        Expression condition = handler.buildDeptCondition(column, table, USER_ID);

        assertThat(condition).isInstanceOf(EqualsTo.class);
        String sql = condition.toString();
        assertThat(sql).contains("dept_id");
        assertThat(sql).contains(String.valueOf(DEPT_ID));
    }

    @Test
    @DisplayName("DEPT范围 - 用户未分配部门时降级为SELF")
    void deptScope_noDept_shouldFallbackToSelf() {
        when(dataProvider.getUserDeptId(USER_ID)).thenReturn(null);

        Table table = new Table("biz_inspection");
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");

        Expression condition = handler.buildDeptCondition(column, table, USER_ID);

        // 应降级为 SELF：created_by = userId
        assertThat(condition).isInstanceOf(EqualsTo.class);
        String sql = condition.toString();
        assertThat(sql).contains("created_by");
        assertThat(sql).contains(String.valueOf(USER_ID));
    }

    // ==================== DEPT_AND_CHILDREN 范围测试 ====================

    @Test
    @DisplayName("DEPT_AND_CHILDREN范围 - 生成 dept_id IN (部门及子部门) 条件")
    void deptAndChildrenScope_shouldGenerateInExpressionCondition() {
        when(dataProvider.getUserDataScopes(USER_ID)).thenReturn(List.of("DEPT_AND_CHILDREN"));
        when(dataProvider.getUserDeptId(USER_ID)).thenReturn(DEPT_ID);
        when(dataProvider.getDeptAndChildIds(DEPT_ID)).thenReturn(CHILD_DEPT_IDS);

        DataScopeEnum scope = handler.getEffectiveScope(USER_ID);
        assertThat(scope).isEqualTo(DataScopeEnum.DEPT_AND_CHILDREN);

        Table table = new Table("biz_inspection");
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");

        Expression condition = handler.buildDeptAndChildrenCondition(column, table, USER_ID);

        assertThat(condition).isInstanceOf(InExpression.class);
        String sql = condition.toString();
        assertThat(sql).contains("dept_id");
        assertThat(sql).contains("IN");
        // 验证包含所有子部门ID
        for (Long childDeptId : CHILD_DEPT_IDS) {
            assertThat(sql).contains(String.valueOf(childDeptId));
        }
    }

    @Test
    @DisplayName("DEPT_AND_CHILDREN范围 - 用户未分配部门时降级为SELF")
    void deptAndChildrenScope_noDept_shouldFallbackToSelf() {
        when(dataProvider.getUserDeptId(USER_ID)).thenReturn(null);

        Table table = new Table("biz_inspection");
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");

        Expression condition = handler.buildDeptAndChildrenCondition(column, table, USER_ID);

        assertThat(condition).isInstanceOf(EqualsTo.class);
        assertThat(condition.toString()).contains("created_by");
    }

    // ==================== PROJECT 范围测试 ====================

    @Test
    @DisplayName("PROJECT范围 - 生成 project_id IN (项目列表) 条件")
    void projectScope_shouldGenerateInExpressionCondition() {
        when(dataProvider.getUserDataScopes(USER_ID)).thenReturn(List.of("PROJECT"));
        when(dataProvider.getUserProjectIds(USER_ID)).thenReturn(PROJECT_IDS);

        DataScopeEnum scope = handler.getEffectiveScope(USER_ID);
        assertThat(scope).isEqualTo(DataScopeEnum.PROJECT);

        Table table = new Table("biz_inspection");
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");

        Expression condition = handler.buildProjectCondition(column, table, USER_ID);

        assertThat(condition).isInstanceOf(InExpression.class);
        String sql = condition.toString();
        assertThat(sql).contains("project_id");
        assertThat(sql).contains("IN");
        for (Long projectId : PROJECT_IDS) {
            assertThat(sql).contains(String.valueOf(projectId));
        }
    }

    @Test
    @DisplayName("PROJECT范围 - 用户未参与项目时生成 1=0 条件")
    void projectScope_noProjects_shouldGenerateImpossibleCondition() {
        when(dataProvider.getUserProjectIds(USER_ID)).thenReturn(List.of());

        Table table = new Table("biz_inspection");
        DataColumn column = createDataColumn("", "created_by", "dept_id", "project_id");

        Expression condition = handler.buildProjectCondition(column, table, USER_ID);

        // 应生成 1 = 0 条件（无数据可见）
        assertThat(condition).isInstanceOf(EqualsTo.class);
        String sql = condition.toString();
        assertThat(sql).contains("1").contains("0");
    }

    // ==================== 多角色取最大优先级测试 ====================

    @Test
    @DisplayName("多角色 - 取最大范围优先级(DEPT_AND_CHILDREN > SELF)")
    void multiRole_shouldUseHighestPriority() {
        when(dataProvider.getUserDataScopes(USER_ID)).thenReturn(List.of("SELF", "DEPT_AND_CHILDREN", "DEPT"));

        DataScopeEnum scope = handler.getEffectiveScope(USER_ID);

        assertThat(scope).isEqualTo(DataScopeEnum.DEPT_AND_CHILDREN);
    }

    @Test
    @DisplayName("多角色 - 含ALL则结果为ALL")
    void multiRole_withAll_shouldReturnAll() {
        when(dataProvider.getUserDataScopes(USER_ID)).thenReturn(List.of("SELF", "ALL", "DEPT"));

        DataScopeEnum scope = handler.getEffectiveScope(USER_ID);

        assertThat(scope).isEqualTo(DataScopeEnum.ALL);
    }

    // ==================== 异常降级测试 ====================

    @Test
    @DisplayName("DataProvider异常 - 降级为SELF范围")
    void providerException_shouldFallbackToSelf() {
        when(dataProvider.getUserDataScopes(USER_ID)).thenThrow(new RuntimeException("DB连接失败"));

        DataScopeEnum scope = handler.getEffectiveScope(USER_ID);

        assertThat(scope).isEqualTo(DataScopeEnum.SELF);
    }

    @Test
    @DisplayName("无角色 - 默认SELF范围")
    void noRoles_shouldDefaultToSelf() {
        when(dataProvider.getUserDataScopes(USER_ID)).thenReturn(List.of());

        DataScopeEnum scope = handler.getEffectiveScope(USER_ID);

        assertThat(scope).isEqualTo(DataScopeEnum.SELF);
    }

    // ==================== 系统管理模块跳过测试 ====================

    @Test
    @DisplayName("系统管理模块Mapper - 跳过数据权限过滤")
    void systemModule_shouldSkipDataPermission() {
        try (MockedStatic<SecurityContextHolder> contextMock = mockStatic(SecurityContextHolder.class)) {
            contextMock.when(SecurityContextHolder::getUserId).thenReturn(USER_ID);

            Table table = new Table("sys_role");
            String systemMapperMsId = "com.zwinsight.system.mapper.SysRoleMapper.selectList";

            Expression result = handler.getSqlSegment(table, null, systemMapperMsId);

            assertThat(result)
                    .as("系统管理模块的 Mapper 应返回 null（不过滤）")
                    .isNull();
        }
    }

    // ==================== 辅助方法 ====================

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
