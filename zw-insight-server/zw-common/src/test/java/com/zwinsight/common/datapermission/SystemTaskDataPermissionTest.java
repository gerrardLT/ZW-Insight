package com.zwinsight.common.datapermission;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.DataPermissionException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 系统任务（定时任务/异步作业）数据权限豁免测试。
 * <p>
 * 背景（2026-09-24 线上实证）：{@code TenantTaskRunner} 只设置 tenantId 不设 userId，
 * 而 {@link ZwDataPermissionHandler} 在 userId==null 时抛 {@link DataPermissionException}，
 * 导致 FundForecastTask 01:15「成功 0/2」、RiskScanTask 02:00 有 3/7 条规则失败，
 * 而任务层仍报「成功 2/2」——定时任务体系实质空转。
 * </p>
 * <p>本测试钉住三点：① 系统任务标记时放行（不过滤、不抛异常）；
 * ② 未标记且无用户上下文时仍必须拒绝（防豁免被滥用）；
 * ③ 未标记的普通用户请求数据权限照常生效（防过度放行）。</p>
 */
class SystemTaskDataPermissionTest {

    /** 测试用带类级 @DataPermission 的 Mapper（模拟 BizPaymentApplyMapper 的注解形态） */
    @DataPermission(value = {
            @DataColumn(projectColumn = "project_id", userColumn = "created_by", deptColumn = "dept_id")
    })
    interface ScopedTestMapper {
    }

    private static final String SCOPED_STATEMENT =
            "com.zwinsight.common.datapermission.SystemTaskDataPermissionTest$ScopedTestMapper.selectList";

    private final DataPermissionDataProvider provider = Mockito.mock(DataPermissionDataProvider.class);
    private final ZwDataPermissionHandler handler = new ZwDataPermissionHandler(provider);

    @AfterEach
    void tearDown() {
        // ThreadLocal 必须清理，否则污染同线程后续用例（线程池复用场景同理）
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("系统任务标记 — 无 userId 也放行（返回 null 不追加过滤条件），定时任务不再整体失败")
    void systemTask_withoutUserId_isExempted() {
        SecurityContextHolder.setTenantId(1L);
        SecurityContextHolder.markSystemTask();
        // 故意不设置 userId：这正是定时任务的真实上下文

        Expression result = handler.getSqlSegment(new Table("biz_payment_apply"), null, SCOPED_STATEMENT);

        assertThat(result).isNull();
        assertThat(SecurityContextHolder.isSystemTask()).isTrue();
    }

    @Test
    @DisplayName("防豁免滥用 — 未标记系统任务且无 userId 时仍必须拒绝（保持原有安全语义）")
    void notSystemTask_withoutUserId_stillRejected() {
        SecurityContextHolder.setTenantId(1L);
        assertThat(SecurityContextHolder.isSystemTask()).isFalse();

        assertThatThrownBy(() -> handler.getSqlSegment(new Table("biz_payment_apply"), null, SCOPED_STATEMENT))
                .isInstanceOf(DataPermissionException.class)
                .hasMessageContaining("无法获取用户上下文");
    }

    @Test
    @DisplayName("防过度放行 — 普通用户请求（未标记系统任务）数据权限照常生效")
    void normalUserRequest_permissionStillApplies() {
        SecurityContextHolder.setTenantId(1L);
        SecurityContextHolder.setUserId(88L);
        when(provider.getUserDataScopes(anyLong())).thenReturn(List.of("SELF"));

        Expression result = handler.getSqlSegment(new Table("biz_payment_apply"), null, SCOPED_STATEMENT);

        assertThat(result).isNotNull();
        assertThat(result.toString()).contains("created_by");
    }

    @Test
    @DisplayName("上下文清理 — clear() 后系统任务标记与租户/用户一并移除（防线程池污染）")
    void clear_removesSystemTaskFlag() {
        SecurityContextHolder.setTenantId(9999L);
        SecurityContextHolder.setUserId(1L);
        SecurityContextHolder.markSystemTask();
        assertThat(SecurityContextHolder.isSystemTask()).isTrue();

        SecurityContextHolder.clear();

        assertThat(SecurityContextHolder.isSystemTask()).isFalse();
        assertThat(SecurityContextHolder.getTenantId()).isNull();
        assertThat(SecurityContextHolder.getUserId()).isNull();
    }

    @Test
    @DisplayName("ALL 范围用户 — 即使未标记系统任务也不追加条件（既有行为不回归）")
    void allScopeUser_returnsNull() {
        SecurityContextHolder.setTenantId(1L);
        SecurityContextHolder.setUserId(1L);
        when(provider.getUserDataScopes(anyLong())).thenReturn(List.of("ALL"));

        Expression result = handler.getSqlSegment(new Table("biz_payment_apply"), null, SCOPED_STATEMENT);

        assertThat(result).isNull();
    }
}
