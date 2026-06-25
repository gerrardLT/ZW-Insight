package com.zwinsight.integration;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.datapermission.DataPermissionDataProvider;
import com.zwinsight.common.datapermission.DataScopeEnum;
import com.zwinsight.common.datapermission.ZwDataPermissionHandler;
import com.zwinsight.project.service.ProjectMemberService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据权限集成测试
 * <p>
 * 验证：
 * <ul>
 *   <li>数据权限拦截器 SQL 拼接正确性</li>
 *   <li>项目成员添加后 PROJECT 数据范围正确联动</li>
 * </ul>
 * </p>
 *
 * 对应需求：R1 (AC 3-10), R3 (AC 7)
 */
@DisplayName("数据权限集成测试")
class DataPermissionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ZwDataPermissionHandler dataPermissionHandler;

    @Autowired
    private DataPermissionDataProvider dataProvider;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long TENANT_ID = 1000L;
    private static final Long USER_A = 2001L;
    private static final Long USER_B = 2002L;
    private static final Long ROLE_PM = 3001L;
    private static final Long ROLE_ADMIN = 3002L;
    private static final Long PROJECT_1 = 5001L;
    private static final Long PROJECT_2 = 5002L;
    private static final Long DEPT_1 = 4001L;
    private static final Long DEPT_2 = 4002L;

    @BeforeEach
    void setupTestData() {
        // 清除残留数据
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user_project");
        jdbcTemplate.update("DELETE FROM biz_project_member WHERE deleted = 0");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_dept");

        // 创建部门
        jdbcTemplate.update(
                "INSERT INTO sys_dept (id, tenant_id, dept_name, parent_id, ancestors) VALUES (?, ?, ?, ?, ?)",
                DEPT_1, TENANT_ID, "研发部", 0, "0");
        jdbcTemplate.update(
                "INSERT INTO sys_dept (id, tenant_id, dept_name, parent_id, ancestors) VALUES (?, ?, ?, ?, ?)",
                DEPT_2, TENANT_ID, "研发一组", DEPT_1, "0," + DEPT_1);

        // 创建角色（不同数据范围）
        jdbcTemplate.update(
                "INSERT INTO sys_role (id, tenant_id, role_name, role_code, data_scope) VALUES (?, ?, ?, ?, ?)",
                ROLE_PM, TENANT_ID, "项目经理", "project_manager", "PROJECT");
        jdbcTemplate.update(
                "INSERT INTO sys_role (id, tenant_id, role_name, role_code, data_scope) VALUES (?, ?, ?, ?, ?)",
                ROLE_ADMIN, TENANT_ID, "系统管理员", "admin", "ALL");

        // 创建用户
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, tenant_id, username, real_name, dept_id, status) VALUES (?, ?, ?, ?, ?, ?)",
                USER_A, TENANT_ID, "user_a", "用户A", DEPT_1, 1);
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, tenant_id, username, real_name, dept_id, status) VALUES (?, ?, ?, ?, ?, ?)",
                USER_B, TENANT_ID, "user_b", "用户B", DEPT_2, 1);

        // 用户A关联角色 PROJECT_MANAGER
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id, tenant_id) VALUES (?, ?, ?)",
                USER_A, ROLE_PM, TENANT_ID);

        // 用户B关联角色 ADMIN（ALL 权限）
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id, tenant_id) VALUES (?, ?, ?)",
                USER_B, ROLE_ADMIN, TENANT_ID);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clear();
    }

    // ==================== 子任务 2：数据权限 SQL 拼接验证 ====================

    @Test
    @DisplayName("SELF 范围 - 仅返回用户本人创建的数据")
    void testSelfScope_onlyReturnsOwnData() {
        // 给用户A设置 SELF 角色
        jdbcTemplate.update("UPDATE sys_role SET data_scope = 'SELF' WHERE id = ?", ROLE_PM);

        DataScopeEnum effectiveScope = dataPermissionHandler.getEffectiveScope(USER_A);
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.SELF);
    }

    @Test
    @DisplayName("ALL 范围 - 返回全部数据（无过滤）")
    void testAllScope_returnsAllData() {
        DataScopeEnum effectiveScope = dataPermissionHandler.getEffectiveScope(USER_B);
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.ALL);
    }

    @Test
    @DisplayName("多角色取最大数据范围")
    void testMultipleRoles_takesMaxScope() {
        // 给用户A再分配一个 ALL 角色
        Long roleAll = 3003L;
        jdbcTemplate.update(
                "INSERT INTO sys_role (id, tenant_id, role_name, role_code, data_scope) VALUES (?, ?, ?, ?, ?)",
                roleAll, TENANT_ID, "超级管理员", "superadmin", "ALL");
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id, tenant_id) VALUES (?, ?, ?)",
                USER_A, roleAll, TENANT_ID);

        // 用户A有 PROJECT 和 ALL 两个角色，应取 ALL
        DataScopeEnum effectiveScope = dataPermissionHandler.getEffectiveScope(USER_A);
        assertThat(effectiveScope).isEqualTo(DataScopeEnum.ALL);
    }

    @Test
    @DisplayName("PROJECT 范围 - 仅返回用户参与的项目数据")
    void testProjectScope_returnsOnlyMemberProjects() {
        // 将用户A添加为项目1的成员
        jdbcTemplate.update(
                "INSERT INTO sys_user_project (user_id, project_id, tenant_id) VALUES (?, ?, ?)",
                USER_A, PROJECT_1, TENANT_ID);

        // 验证用户A的项目ID列表
        List<Long> projectIds = projectMemberService.getUserProjectIds(USER_A);
        assertThat(projectIds).containsExactly(PROJECT_1);
        assertThat(projectIds).doesNotContain(PROJECT_2);
    }

    @Test
    @DisplayName("DEPT 范围 - 基于用户部门过滤")
    void testDeptScope_filtersByUserDept() {
        // 用户A的部门为 DEPT_1
        Long deptId = dataProvider.getUserDeptId(USER_A);
        assertThat(deptId).isEqualTo(DEPT_1);
    }

    @Test
    @DisplayName("DEPT_AND_CHILDREN 范围 - 包含子部门")
    void testDeptAndChildrenScope_includesSubDepts() {
        List<Long> deptIds = dataProvider.getDeptAndChildIds(DEPT_1);
        assertThat(deptIds).contains(DEPT_1, DEPT_2);
    }

    @Test
    @DisplayName("数据权限配置变更后实时生效")
    void testDataScopeChangeEffectImmediately() {
        // 初始：用户A的角色为 PROJECT
        DataScopeEnum scopeBefore = dataPermissionHandler.getEffectiveScope(USER_A);
        assertThat(scopeBefore).isEqualTo(DataScopeEnum.PROJECT);

        // 变更角色数据范围为 DEPT
        jdbcTemplate.update("UPDATE sys_role SET data_scope = 'DEPT' WHERE id = ?", ROLE_PM);

        // 再次查询，应立即生效（无缓存）
        DataScopeEnum scopeAfter = dataPermissionHandler.getEffectiveScope(USER_A);
        assertThat(scopeAfter).isEqualTo(DataScopeEnum.DEPT);
    }

    // ==================== 子任务 3：项目成员 → 数据权限 PROJECT 范围联动 ====================

    @Test
    @DisplayName("添加项目成员后 - 用户 PROJECT 范围立即包含新项目")
    @Transactional
    @Rollback
    void testAddMember_projectScopeUpdatesImmediately() {
        // 设置安全上下文
        SecurityContextHolder.setUserId(USER_A);
        SecurityContextHolder.setTenantId(TENANT_ID);

        // 初始：用户A不参与任何项目
        List<Long> projectsBefore = projectMemberService.getUserProjectIds(USER_A);
        assertThat(projectsBefore).isEmpty();

        // 将用户A添加为项目1的成员
        projectMemberService.addCreatorAsProjectManager(PROJECT_1, USER_A, "用户A");

        // 验证：项目列表中应包含 PROJECT_1
        List<Long> projectsAfter = projectMemberService.getUserProjectIds(USER_A);
        assertThat(projectsAfter).contains(PROJECT_1);
    }

    @Test
    @DisplayName("移除项目成员后 - 用户 PROJECT 范围不再包含该项目")
    @Transactional
    @Rollback
    void testRemoveMember_projectScopeRemovesProject() {
        // 设置安全上下文
        SecurityContextHolder.setUserId(USER_A);
        SecurityContextHolder.setTenantId(TENANT_ID);

        // 先添加用户A为项目1和项目2的成员
        projectMemberService.addCreatorAsProjectManager(PROJECT_1, USER_A, "用户A");
        projectMemberService.addCreatorAsProjectManager(PROJECT_2, USER_A, "用户A");

        // 另加一个 PM 到项目1（避免唯一PM保护拒绝移除）
        jdbcTemplate.update(
                "INSERT INTO sys_user_project (user_id, project_id, tenant_id) VALUES (?, ?, ?)",
                USER_B, PROJECT_1, TENANT_ID);

        // 移除用户A从项目1
        projectMemberService.removeMember(PROJECT_1, USER_A);

        // 验证：项目列表不再包含 PROJECT_1
        List<Long> projects = projectMemberService.getUserProjectIds(USER_A);
        assertThat(projects).doesNotContain(PROJECT_1);
        assertThat(projects).contains(PROJECT_2);
    }
}
