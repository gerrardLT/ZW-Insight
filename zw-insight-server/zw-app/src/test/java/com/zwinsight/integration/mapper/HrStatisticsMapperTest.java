package com.zwinsight.integration.mapper;

import com.zwinsight.hr.domain.vo.HrStatisticsVO;
import com.zwinsight.hr.mapper.HrStatisticsMapper;
import com.zwinsight.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HrStatisticsMapper 集成测试（Testcontainers MySQL）
 * <p>
 * 使用 MySQL 专有函数 DATE_FORMAT、CURDATE、DATE_SUB、TIMESTAMPDIFF、FIELD，
 * 必须在真实 MySQL 环境中运行。
 * </p>
 */
class HrStatisticsMapperTest extends BaseIntegrationTest {

    @Autowired
    private HrStatisticsMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM biz_resign_apply");
        jdbc.execute("DELETE FROM biz_entry_apply");
        jdbc.execute("DELETE FROM sys_user");
        jdbc.execute("DELETE FROM sys_org");
        jdbc.execute("DELETE FROM sys_post");

        // 机构
        jdbc.update("INSERT INTO sys_org (id, org_name, org_code, tenant_id, deleted, version) "
                + "VALUES (1, '技术部', 'TECH', ?, 0, 0)", TENANT_ID);
        jdbc.update("INSERT INTO sys_org (id, org_name, org_code, tenant_id, deleted, version) "
                + "VALUES (2, '市场部', 'MKT', ?, 0, 0)", TENANT_ID);

        // 岗位
        jdbc.update("INSERT INTO sys_post (id, post_name, post_code, tenant_id, deleted, version) "
                + "VALUES (1, '开发工程师', 'DEV', ?, 0, 0)", TENANT_ID);
        jdbc.update("INSERT INTO sys_post (id, post_name, post_code, tenant_id, deleted, version) "
                + "VALUES (2, '产品经理', 'PM', ?, 0, 0)", TENANT_ID);

        // 在职用户
        jdbc.update("INSERT INTO sys_user (id, username, org_id, post_id, status, tenant_id, deleted, version, created_at) "
                + "VALUES (1, 'user1', 1, 1, 1, ?, 0, 0, NOW())", TENANT_ID);
        jdbc.update("INSERT INTO sys_user (id, username, org_id, post_id, status, tenant_id, deleted, version, created_at) "
                + "VALUES (2, 'user2', 1, 1, 1, ?, 0, 0, NOW())", TENANT_ID);
        jdbc.update("INSERT INTO sys_user (id, username, org_id, post_id, status, tenant_id, deleted, version, created_at) "
                + "VALUES (3, 'user3', 2, 2, 1, ?, 0, 0, NOW())", TENANT_ID);
        // 已停用用户
        jdbc.update("INSERT INTO sys_user (id, username, org_id, post_id, status, tenant_id, deleted, version, created_at) "
                + "VALUES (4, 'user4', 1, 1, 0, ?, 0, 0, NOW())", TENANT_ID);
        // 已删除用户
        jdbc.update("INSERT INTO sys_user (id, username, org_id, post_id, status, tenant_id, deleted, version, created_at) "
                + "VALUES (5, 'user5', 1, 1, 1, ?, 1, 0, NOW())", TENANT_ID);

        // 入职申请（本月）
        jdbc.update("INSERT INTO biz_entry_apply (id, entry_date, status, tenant_id, deleted, version) "
                + "VALUES (1, CURDATE(), 'APPROVED', ?, 0, 0)", TENANT_ID);
        jdbc.update("INSERT INTO biz_entry_apply (id, entry_date, status, tenant_id, deleted, version) "
                + "VALUES (2, CURDATE(), 'APPROVED', ?, 0, 0)", TENANT_ID);
        // 草稿状态（不计入）
        jdbc.update("INSERT INTO biz_entry_apply (id, entry_date, status, tenant_id, deleted, version) "
                + "VALUES (3, CURDATE(), 'DRAFT', ?, 0, 0)", TENANT_ID);

        // 离职申请（本月）
        jdbc.update("INSERT INTO biz_resign_apply (id, resign_date, status, tenant_id, deleted, version) "
                + "VALUES (1, CURDATE(), 'APPROVED', ?, 0, 0)", TENANT_ID);
    }

    @Test
    @DisplayName("countActiveUsers — 查询在职总人数")
    void should_count_active_users() {
        Long count = mapper.countActiveUsers(TENANT_ID);

        // 3个在职用户（status=1 且 deleted=0）
        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("countMonthlyEntry — 查询本月入职人数")
    void should_count_monthly_entry() {
        Long count = mapper.countMonthlyEntry(TENANT_ID);

        // 2个已审批的入职申请
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("countMonthlyResign — 查询本月离职人数")
    void should_count_monthly_resign() {
        Long count = mapper.countMonthlyResign(TENANT_ID);

        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("statByDept — 按部门统计人数")
    void should_stat_by_dept() {
        List<HrStatisticsVO.DeptStatItem> items = mapper.statByDept(TENANT_ID);

        assertThat(items).isNotEmpty();
        // 技术部 2 人，市场部 1 人
        assertThat(items).extracting(HrStatisticsVO.DeptStatItem::getDeptName)
                .contains("技术部", "市场部");

        HrStatisticsVO.DeptStatItem tech = items.stream()
                .filter(i -> "技术部".equals(i.getDeptName()))
                .findFirst().orElseThrow();
        assertThat(tech.getCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("statByPost — 按岗位统计人数")
    void should_stat_by_post() {
        List<HrStatisticsVO.PostStatItem> items = mapper.statByPost(TENANT_ID);

        assertThat(items).isNotEmpty();
        assertThat(items).extracting(HrStatisticsVO.PostStatItem::getPostName)
                .contains("开发工程师", "产品经理");
    }

    @Test
    @DisplayName("statBySeniority — 按工龄段统计（MySQL FIELD 函数排序）")
    void should_stat_by_seniority() {
        List<HrStatisticsVO.SeniorityStatItem> items = mapper.statBySeniority(TENANT_ID);

        assertThat(items).isNotEmpty();
        // 所有用户都是刚创建的，工龄 < 1 年
        assertThat(items).anyMatch(i -> "0-1年".equals(i.getRange()));
    }

    @Test
    @DisplayName("statEntryTrend — 近12个月入职趋势")
    void should_stat_entry_trend() {
        List<HrStatisticsVO.TrendStatItem> items = mapper.statEntryTrend(TENANT_ID);

        assertThat(items).isNotEmpty();
        // 本月有入职记录
        HrStatisticsVO.TrendStatItem currentMonth = items.get(items.size() - 1);
        assertThat(currentMonth.getEntryCount()).isEqualTo(2L);
        assertThat(currentMonth.getResignCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("statResignTrend — 近12个月离职趋势")
    void should_stat_resign_trend() {
        List<HrStatisticsVO.TrendStatItem> items = mapper.statResignTrend(TENANT_ID);

        assertThat(items).isNotEmpty();
        HrStatisticsVO.TrendStatItem currentMonth = items.get(items.size() - 1);
        assertThat(currentMonth.getResignCount()).isEqualTo(1L);
        assertThat(currentMonth.getEntryCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("不同租户数据隔离")
    void should_isolate_by_tenant() {
        Long count = mapper.countActiveUsers(999L);

        assertThat(count).isEqualTo(0L);
    }
}
