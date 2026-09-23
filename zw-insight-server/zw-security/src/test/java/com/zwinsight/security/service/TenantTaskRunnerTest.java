package com.zwinsight.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.security.domain.SysTenant;
import com.zwinsight.security.mapper.SysTenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link TenantTaskRunner} 上下文与失败隔离测试。
 * <p>钉住 2026-09-24 修复的两个缺陷：
 * ① 定时任务必须带「系统任务」标记，否则数据权限处理器因 userId==null 抛异常导致任务空转
 * （线上实证 FundForecastTask 成功 0/2）；
 * ② 单租户失败不中断其他租户，且异常不会伪装成成功。</p>
 */
@ExtendWith(MockitoExtension.class)
class TenantTaskRunnerTest {

    @Mock
    private SysTenantMapper tenantMapper;

    @InjectMocks
    private TenantTaskRunner runner;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    private SysTenant tenant(Long id) {
        SysTenant t = new SysTenant();
        t.setId(id);
        t.setStatus(1);
        return t;
    }

    @Test
    @DisplayName("正常路径 — 逐租户设置 tenantId 且标记系统任务（数据权限据此放行）")
    void runForActiveTenants_setsTenantAndSystemTaskFlag() {
        when(tenantMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(tenant(1L), tenant(9999L)));
        List<String> observed = new ArrayList<>();

        runner.runForActiveTenants("测试任务", tenantId ->
                observed.add(tenantId + "|tenant=" + SecurityContextHolder.getTenantId()
                        + "|systemTask=" + SecurityContextHolder.isSystemTask()));

        assertThat(observed).containsExactly("1|tenant=1|systemTask=true", "9999|tenant=9999|systemTask=true");
    }

    @Test
    @DisplayName("上下文清理 — 执行结束后 tenantId/userId/系统任务标记全部移除（防线程池复用污染）")
    void runForActiveTenants_clearsContextAfterExecution() {
        when(tenantMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(tenant(1L)));

        runner.runForActiveTenants("测试任务", tenantId -> {
            SecurityContextHolder.setUserId(77L); // 业务代码若自行设置用户，也必须被清理
        });

        assertThat(SecurityContextHolder.getTenantId()).isNull();
        assertThat(SecurityContextHolder.getUserId()).isNull();
        assertThat(SecurityContextHolder.isSystemTask()).isFalse();
    }

    @Test
    @DisplayName("失败隔离 — 首个租户抛异常不影响后续租户执行")
    void singleTenantFailure_doesNotStopOthers() {
        when(tenantMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(tenant(1L), tenant(2L)));
        List<Long> executed = new ArrayList<>();

        runner.runForActiveTenants("测试任务", tenantId -> {
            executed.add(tenantId);
            if (tenantId == 1L) {
                throw new IllegalStateException("模拟租户1失败");
            }
        });

        assertThat(executed).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("全部失败 — Runner 自身不抛异常（由 ERROR 日志告警），避免调度线程被打断")
    void allTenantsFail_runnerSwallowsButDoesNotPropagate() {
        when(tenantMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(tenant(1L), tenant(2L)));

        assertThatCode(() -> runner.runForActiveTenants("测试任务", tenantId -> {
            throw new IllegalStateException("全部失败");
        })).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("边界路径 — 无正常租户时不执行任何 action，也不抛异常")
    void noActiveTenants_noActionNoException() {
        when(tenantMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        List<Long> executed = new ArrayList<>();

        assertThatCode(() -> runner.runForActiveTenants("测试任务", executed::add))
                .doesNotThrowAnyException();
        assertThat(executed).isEmpty();
    }
}
