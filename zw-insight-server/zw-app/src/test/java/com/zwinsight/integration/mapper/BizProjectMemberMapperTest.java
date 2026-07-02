package com.zwinsight.integration.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.integration.BaseIntegrationTest;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BizProjectMemberMapper 集成测试（Testcontainers MySQL）
 * <p>
 * 测试 JSON_CONTAINS 在项目角色 JSON 数组中的匹配逻辑。
 * </p>
 */
class BizProjectMemberMapperTest extends BaseIntegrationTest {

    @Autowired
    private BizProjectMemberMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final Long PROJECT_ID = 1L;
    private static final Long OTHER_PROJECT_ID = 2L;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM biz_project_member");

        // 项目1 成员
        jdbc.update("INSERT INTO biz_project_member (id, project_id, user_id, user_name, project_roles, status, deleted, version) "
                        + "VALUES (1, ?, 101, '张三', '[\"PROJECT_MANAGER\", \"CONSTRUCTOR\"]', 1, 0, 0)",
                PROJECT_ID);
        jdbc.update("INSERT INTO biz_project_member (id, project_id, user_id, user_name, project_roles, status, deleted, version) "
                        + "VALUES (2, ?, 102, '李四', '[\"CONSTRUCTOR\"]', 1, 0, 0)",
                PROJECT_ID);
        jdbc.update("INSERT INTO biz_project_member (id, project_id, user_id, user_name, project_roles, status, deleted, version) "
                        + "VALUES (3, ?, 103, '王五', '[\"SAFETY_OFFICER\"]', 1, 0, 0)",
                PROJECT_ID);
        // 已删除成员
        jdbc.update("INSERT INTO biz_project_member (id, project_id, user_id, user_name, project_roles, status, deleted, version) "
                        + "VALUES (4, ?, 104, '赵六', '[\"PROJECT_MANAGER\"]', 1, 1, 0)",
                PROJECT_ID);
        // 已失效成员（status=2）
        jdbc.update("INSERT INTO biz_project_member (id, project_id, user_id, user_name, project_roles, status, deleted, version) "
                        + "VALUES (5, ?, 105, '孙七', '[\"CONSTRUCTOR\"]', 2, 0, 0)",
                PROJECT_ID);
        // 其他项目
        jdbc.update("INSERT INTO biz_project_member (id, project_id, user_id, user_name, project_roles, status, deleted, version) "
                        + "VALUES (6, ?, 201, '其他项目成员', '[\"PROJECT_MANAGER\"]', 1, 0, 0)",
                OTHER_PROJECT_ID);
    }

    @Test
    @DisplayName("selectMemberPage — 查询全部成员（不按角色筛选）")
    void should_select_all_members_without_role_filter() {
        Page<BizProjectMember> page = new Page<>(1, 10);
        mapper.selectMemberPage(page, PROJECT_ID, null);

        // 3个有效成员（已删除和已失效的不计入）
        assertThat(page.getTotal()).isEqualTo(3);
    }

    @Test
    @DisplayName("selectMemberPage — 按角色 PROJECT_MANAGER 筛选")
    void should_filter_by_role_project_manager() {
        Page<BizProjectMember> page = new Page<>(1, 10);
        mapper.selectMemberPage(page, PROJECT_ID, "PROJECT_MANAGER");

        // 只有张三包含 PROJECT_MANAGER 角色
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getUserName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("selectMemberPage — 按角色 CONSTRUCTOR 筛选")
    void should_filter_by_role_constructor() {
        Page<BizProjectMember> page = new Page<>(1, 10);
        mapper.selectMemberPage(page, PROJECT_ID, "CONSTRUCTOR");

        // 张三和李四都包含 CONSTRUCTOR 角色
        assertThat(page.getTotal()).isEqualTo(2);
    }

    @Test
    @DisplayName("selectMemberPage — 按不存在的角色筛选返回空")
    void should_return_empty_for_unknown_role() {
        Page<BizProjectMember> page = new Page<>(1, 10);
        mapper.selectMemberPage(page, PROJECT_ID, "NONEXISTENT_ROLE");

        assertThat(page.getTotal()).isEqualTo(0);
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("selectMemberPage — 不同项目数据隔离")
    void should_isolate_by_project() {
        Page<BizProjectMember> page = new Page<>(1, 10);
        mapper.selectMemberPage(page, OTHER_PROJECT_ID, null);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getUserName()).isEqualTo("其他项目成员");
    }

    @Test
    @DisplayName("selectMemberPage — 分页正常工作")
    void should_paginate_correctly() {
        Page<BizProjectMember> page = new Page<>(1, 2);
        mapper.selectMemberPage(page, PROJECT_ID, null);

        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getPages()).isEqualTo(2);
    }
}
