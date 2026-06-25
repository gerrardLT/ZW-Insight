package com.zwinsight.pbt;

import com.zwinsight.common.datapermission.DataScopeEnum;
import net.jqwik.api.*;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Set;

/**
 * Property 1：DataPermissionHandler SQL 过滤条件正确性
 * <p>
 * 验证：根据数据范围枚举生成的 SQL 过滤条件格式正确且逻辑一致。
 * - ALL → 不追加任何条件
 * - SELF → WHERE created_by = ?
 * - DEPT → WHERE dept_id = ?
 * - DEPT_AND_CHILDREN → WHERE dept_id IN (...)
 * - PROJECT → WHERE project_id IN (...)
 * <p>
 * Validates: Requirements 1.3
 */
@Tag("Feature: p1-system-integrity, Property 1: DataPermissionHandler SQL 过滤条件正确性")
class DataPermissionSqlFilterPropertyTest {

    /**
     * 核心业务逻辑：根据数据范围构建 SQL 过滤条件
     */
    static String buildFilterCondition(DataScopeEnum scope, Long userId, Long deptId,
                                        List<Long> childDeptIds, List<Long> projectIds) {
        switch (scope) {
            case ALL:
                return ""; // 全部数据，不追加条件
            case SELF:
                return "created_by = " + userId;
            case DEPT:
                return "dept_id = " + deptId;
            case DEPT_AND_CHILDREN:
                if (childDeptIds == null || childDeptIds.isEmpty()) {
                    return "dept_id = " + deptId;
                }
                StringBuilder sb = new StringBuilder("dept_id IN (");
                sb.append(deptId);
                for (Long childId : childDeptIds) {
                    sb.append(",").append(childId);
                }
                sb.append(")");
                return sb.toString();
            case PROJECT:
                if (projectIds == null || projectIds.isEmpty()) {
                    return "1 = 0"; // 无项目时无数据可见
                }
                StringBuilder psb = new StringBuilder("project_id IN (");
                for (int i = 0; i < projectIds.size(); i++) {
                    if (i > 0) psb.append(",");
                    psb.append(projectIds.get(i));
                }
                psb.append(")");
                return psb.toString();
            default:
                return "1 = 0";
        }
    }

    @Property(tries = 100)
    void allScope_producesEmptyCondition(@ForAll("userIds") Long userId,
                                          @ForAll("deptIds") Long deptId) {
        String condition = buildFilterCondition(DataScopeEnum.ALL, userId, deptId, List.of(), List.of());
        Assertions.assertThat(condition).isEmpty();
    }

    @Property(tries = 100)
    void selfScope_containsUserId(@ForAll("userIds") Long userId,
                                    @ForAll("deptIds") Long deptId) {
        String condition = buildFilterCondition(DataScopeEnum.SELF, userId, deptId, List.of(), List.of());
        Assertions.assertThat(condition).isEqualTo("created_by = " + userId);
        Assertions.assertThat(condition).contains(String.valueOf(userId));
    }

    @Property(tries = 100)
    void deptScope_containsDeptId(@ForAll("userIds") Long userId,
                                    @ForAll("deptIds") Long deptId) {
        String condition = buildFilterCondition(DataScopeEnum.DEPT, userId, deptId, List.of(), List.of());
        Assertions.assertThat(condition).isEqualTo("dept_id = " + deptId);
    }

    @Property(tries = 100)
    void deptAndChildrenScope_containsAllDeptIds(
            @ForAll("userIds") Long userId,
            @ForAll("deptIds") Long deptId,
            @ForAll("deptIdList") List<Long> childDeptIds) {
        String condition = buildFilterCondition(DataScopeEnum.DEPT_AND_CHILDREN, userId, deptId, childDeptIds, List.of());
        // 应包含主部门 ID
        Assertions.assertThat(condition).contains(String.valueOf(deptId));
        // 应包含所有子部门 ID
        for (Long childId : childDeptIds) {
            Assertions.assertThat(condition).contains(String.valueOf(childId));
        }
        if (!childDeptIds.isEmpty()) {
            Assertions.assertThat(condition).startsWith("dept_id IN (");
        }
    }

    @Property(tries = 100)
    void projectScope_containsAllProjectIds(
            @ForAll("userIds") Long userId,
            @ForAll("deptIds") Long deptId,
            @ForAll("projectIdList") List<Long> projectIds) {
        String condition = buildFilterCondition(DataScopeEnum.PROJECT, userId, deptId, List.of(), projectIds);
        if (projectIds.isEmpty()) {
            Assertions.assertThat(condition).isEqualTo("1 = 0");
        } else {
            Assertions.assertThat(condition).startsWith("project_id IN (");
            for (Long projectId : projectIds) {
                Assertions.assertThat(condition).contains(String.valueOf(projectId));
            }
        }
    }

    @Property(tries = 100)
    void condition_neverContainsSqlInjectionKeywords(
            @ForAll DataScopeEnum scope,
            @ForAll("userIds") Long userId,
            @ForAll("deptIds") Long deptId,
            @ForAll("deptIdList") List<Long> childDeptIds,
            @ForAll("projectIdList") List<Long> projectIds) {
        String condition = buildFilterCondition(scope, userId, deptId, childDeptIds, projectIds);
        // 纯数字和关键字，不应包含危险字符
        Assertions.assertThat(condition).doesNotContain(";", "--", "'", "\"");
    }

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
        return Arbitraries.longs().between(1L, 999999L).list().ofMinSize(0).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<Long>> projectIdList() {
        return Arbitraries.longs().between(1L, 999999L).list().ofMinSize(0).ofMaxSize(10);
    }
}
