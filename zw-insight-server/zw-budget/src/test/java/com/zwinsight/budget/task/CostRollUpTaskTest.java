package com.zwinsight.budget.task;

import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.budget.service.CostRollUpService;
import com.zwinsight.security.service.TenantTaskRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CostRollUpTask} 单元测试（成本归集定时任务）。
 *
 * <p>本任务是「源单据 → CBS 成本账户」自动对账的唯一兜底入口。归集不跑或跑错，
 * 看板上的承诺额/实际额就会长期停在旧值，项目经理据此做的判断全是错的，
 * 因此它的三类保护必须被钉住：
 * <ol>
 *   <li><b>集群安全</b>：Redis 分布式锁未拿到就整轮跳过；Redis 异常时选择「不执行」而非「裸跑」；
 *       无论租户遍历是否抛异常，锁都必须在 finally 中释放。</li>
 *   <li><b>单点故障隔离</b>：查询项目列表失败只跳过本租户；单个项目归集失败不影响其余项目。</li>
 *   <li><b>单轮上限</b>：项目数超过 {@code MAX_PROJECTS_PER_RUN=200} 时只处理前 200 个，
 *       其余留待下轮（最终一致），避免单轮跑太久。</li>
 * </ol>
 *
 * <p>{@code doExecute()} 为包级可见，本测试与其同包，可直接调用以隔离测试单租户逻辑，
 * 不必每次都穿过分布式锁与租户遍历。
 */
@ExtendWith(MockitoExtension.class)
class CostRollUpTaskTest {

    /** 与源码 LOCK_KEY 常量一致；此处硬编码以钉住 key 不被无意改动（改动会导致多实例锁不住）。 */
    private static final String LOCK_KEY = "task:cost-rollup:lock";

    /** 与源码 MAX_PROJECTS_PER_RUN 一致。 */
    private static final int MAX_PROJECTS_PER_RUN = 200;

    @Mock private CostRollUpService costRollUpService;
    @Mock private BizCostAccountMapper costAccountMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private TenantTaskRunner tenantTaskRunner;

    @InjectMocks
    private CostRollUpTask task;

    // ==================== 构造工具 ====================

    /** 构造一份归集报告。 */
    private static CostRollUpService.RollupReport report(Long projectId, int posted,
                                                         int unmappedCount, int failedCount) {
        CostRollUpService.RollupReport r = new CostRollUpService.RollupReport();
        r.setProjectId(projectId);
        r.setPostedCount(posted);
        r.setSourceDocCount(posted + unmappedCount + failedCount);

        List<CostRollUpService.UnmappedDoc> unmapped = new ArrayList<>();
        for (int i = 0; i < unmappedCount; i++) {
            CostRollUpService.UnmappedDoc d = new CostRollUpService.UnmappedDoc();
            d.setCategory("MATERIAL");
            d.setSourceType("CONTRACT");
            d.setSourceId("C-" + i);
            d.setSourceNumber("HT-2026-" + i);
            d.setAmount(new BigDecimal("1000"));
            d.setReason("科目下存在多个账户，需显式绑定");
            unmapped.add(d);
        }
        r.setUnmapped(unmapped);

        List<CostRollUpService.FailedSync> failed = new ArrayList<>();
        for (int i = 0; i < failedCount; i++) {
            CostRollUpService.FailedSync f = new CostRollUpService.FailedSync();
            f.setAccountId(900L + i);
            f.setAccountCode("CB-9" + i);
            f.setAmountType("COMMITMENT");
            f.setDelta(new BigDecimal("500"));
            f.setReason("账户已锁定");
            failed.add(f);
        }
        r.setFailed(failed);
        return r;
    }

    // =====================================================================
    // execute：分布式锁与租户遍历
    // =====================================================================

    @Nested
    @DisplayName("execute：分布式锁 / 租户遍历 / 锁释放")
    class ExecuteTests {

        @Test
        @DisplayName("未拿到锁：整轮跳过，不遍历租户也不释放别人的锁")
        void lockNotAcquired_skipsWholeRun() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq("locked"), eq(30L), eq(TimeUnit.MINUTES)))
                    .thenReturn(false);

            task.execute();

            verify(tenantTaskRunner, never()).runForActiveTenants(anyString(), any());
            // 没拿到锁就不能删 key，否则会把正在执行的那个实例的锁删掉
            verify(stringRedisTemplate, never()).delete(LOCK_KEY);
        }

        @Test
        @DisplayName("setIfAbsent 返回 null：按未获取处理（Boolean.TRUE.equals 的防御）")
        void lockReturnsNull_treatedAsNotAcquired() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(null);

            task.execute();

            verify(tenantTaskRunner, never()).runForActiveTenants(anyString(), any());
        }

        @Test
        @DisplayName("拿到锁：以任务名「成本归集」遍历活跃租户，并在结束后释放锁")
        void lockAcquired_runsTenantsThenReleasesLock() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(LOCK_KEY), eq("locked"), eq(30L), eq(TimeUnit.MINUTES)))
                    .thenReturn(true);

            task.execute();

            verify(tenantTaskRunner).runForActiveTenants(eq("成本归集"), any());
            verify(stringRedisTemplate).delete(LOCK_KEY);
        }

        @Test
        @DisplayName("Redis 取锁异常：选择「不执行」而非「裸跑」，且不向上抛异常")
        void redisThrowsOnAcquire_skipsQuietly() {
            when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis 不可用"));

            assertThatCode(() -> task.execute()).doesNotThrowAnyException();

            verify(tenantTaskRunner, never()).runForActiveTenants(anyString(), any());
            verify(stringRedisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("租户遍历抛异常：异常向上抛出，但锁仍在 finally 中释放")
        void tenantRunnerThrows_lockStillReleased() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            doThrow(new RuntimeException("租户遍历失败"))
                    .when(tenantTaskRunner).runForActiveTenants(anyString(), any());

            assertThatThrownBy(() -> task.execute())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("租户遍历失败");

            // finally 必须释放锁，否则要等 30 分钟 TTL 才能再跑
            verify(stringRedisTemplate).delete(LOCK_KEY);
        }

        @Test
        @DisplayName("Redis 释放锁异常：被吞掉不外抛（TTL 到期会自动释放）")
        void redisThrowsOnRelease_swallowed() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            when(stringRedisTemplate.delete(LOCK_KEY)).thenThrow(new RuntimeException("Redis 不可用"));

            assertThatCode(() -> task.execute()).doesNotThrowAnyException();

            verify(tenantTaskRunner).runForActiveTenants(eq("成本归集"), any());
        }
    }

    // =====================================================================
    // doExecute：单租户归集逻辑
    // =====================================================================

    @Nested
    @DisplayName("doExecute：项目枚举 / 单轮上限 / 故障隔离")
    class DoExecuteTests {

        @Test
        @DisplayName("查询待归集项目失败：只跳过本租户，不抛异常也不归集")
        void selectProjectIdsThrows_caughtAndReturns() {
            when(costAccountMapper.selectProjectIdsWithAccounts())
                    .thenThrow(new RuntimeException("DB 连接中断"));

            assertThatCode(() -> task.doExecute()).doesNotThrowAnyException();

            verify(costRollUpService, never()).rollup(anyLong());
        }

        @Test
        @DisplayName("项目列表为 null：安全返回，不抛 NPE")
        void nullProjectIds_returns() {
            when(costAccountMapper.selectProjectIdsWithAccounts()).thenReturn(null);

            assertThatCode(() -> task.doExecute()).doesNotThrowAnyException();

            verify(costRollUpService, never()).rollup(anyLong());
        }

        @Test
        @DisplayName("本租户无已建 CBS 账户的项目：跳过归集")
        void emptyProjectIds_returns() {
            when(costAccountMapper.selectProjectIdsWithAccounts()).thenReturn(Collections.emptyList());

            task.doExecute();

            verify(costRollUpService, never()).rollup(anyLong());
        }

        @Test
        @DisplayName("正常路径：逐个项目归集")
        void multipleProjects_rollsUpEach() {
            when(costAccountMapper.selectProjectIdsWithAccounts()).thenReturn(List.of(1L, 2L, 3L));
            when(costRollUpService.rollup(1L)).thenReturn(report(1L, 3, 0, 0));
            when(costRollUpService.rollup(2L)).thenReturn(report(2L, 5, 0, 0));
            when(costRollUpService.rollup(3L)).thenReturn(report(3L, 0, 0, 0));

            task.doExecute();

            verify(costRollUpService).rollup(1L);
            verify(costRollUpService).rollup(2L);
            verify(costRollUpService).rollup(3L);
            verify(costRollUpService, times(3)).rollup(anyLong());
        }

        @Test
        @DisplayName("故障隔离：单个项目归集抛异常，其余项目照常处理")
        void singleProjectFails_othersStillProcessed() {
            when(costAccountMapper.selectProjectIdsWithAccounts()).thenReturn(List.of(1L, 2L, 3L));
            when(costRollUpService.rollup(1L)).thenReturn(report(1L, 2, 0, 0));
            when(costRollUpService.rollup(2L)).thenThrow(new RuntimeException("项目 2 归集失败"));
            when(costRollUpService.rollup(3L)).thenReturn(report(3L, 4, 0, 0));

            assertThatCode(() -> task.doExecute()).doesNotThrowAnyException();

            // 关键：失败不能中断循环
            verify(costRollUpService).rollup(3L);
            verify(costRollUpService, times(3)).rollup(anyLong());
        }

        @Test
        @DisplayName("单轮上限：201 个项目只处理前 200 个，第 201 个留待下轮")
        void exceedsMaxProjects_truncatesToFirst200() {
            List<Long> projectIds = LongStream.rangeClosed(1, MAX_PROJECTS_PER_RUN + 1)
                    .boxed().collect(java.util.stream.Collectors.toList());
            when(costAccountMapper.selectProjectIdsWithAccounts()).thenReturn(projectIds);
            when(costRollUpService.rollup(anyLong())).thenReturn(report(1L, 1, 0, 0));

            task.doExecute();

            verify(costRollUpService, times(MAX_PROJECTS_PER_RUN)).rollup(anyLong());
            verify(costRollUpService, never()).rollup(eq((long) MAX_PROJECTS_PER_RUN + 1));
        }

        @Test
        @DisplayName("恰好 200 个项目：不触发截断，全部处理")
        void exactlyMaxProjects_noTruncation() {
            List<Long> projectIds = LongStream.rangeClosed(1, MAX_PROJECTS_PER_RUN)
                    .boxed().collect(java.util.stream.Collectors.toList());
            when(costAccountMapper.selectProjectIdsWithAccounts()).thenReturn(projectIds);
            when(costRollUpService.rollup(anyLong())).thenReturn(report(1L, 1, 0, 0));

            task.doExecute();

            verify(costRollUpService, times(MAX_PROJECTS_PER_RUN)).rollup(anyLong());
        }

        @Test
        @DisplayName("报告含待绑定单据：走 WARN 分支且不抛异常（需人工介入的信号）")
        void reportWithUnmapped_exercisesWarnBranch() {
            when(costAccountMapper.selectProjectIdsWithAccounts()).thenReturn(List.of(1L));
            when(costRollUpService.rollup(1L)).thenReturn(report(1L, 2, 3, 0));

            assertThatCode(() -> task.doExecute()).doesNotThrowAnyException();

            verify(costRollUpService).rollup(1L);
        }

        @Test
        @DisplayName("报告含记账失败明细：走 WARN 分支读取首条 reason 且不抛异常")
        void reportWithFailed_exercisesWarnBranch() {
            when(costAccountMapper.selectProjectIdsWithAccounts()).thenReturn(List.of(1L));
            when(costRollUpService.rollup(1L)).thenReturn(report(1L, 0, 0, 2));

            assertThatCode(() -> task.doExecute()).doesNotThrowAnyException();

            verify(costRollUpService).rollup(1L);
        }

        @Test
        @DisplayName("报告同时含待绑定与失败明细：两个 WARN 分支都走到")
        void reportWithBothUnmappedAndFailed_exercisesBothWarnBranches() {
            when(costAccountMapper.selectProjectIdsWithAccounts()).thenReturn(List.of(1L));
            when(costRollUpService.rollup(1L)).thenReturn(report(1L, 1, 2, 3));

            assertThatCode(() -> task.doExecute()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("全部项目归集零记账（幂等命中）：不抛异常，正常收尾")
        void allProjectsZeroPosted_completesNormally() {
            when(costAccountMapper.selectProjectIdsWithAccounts()).thenReturn(List.of(1L, 2L));
            when(costRollUpService.rollup(anyLong())).thenReturn(report(1L, 0, 0, 0));

            assertThatCode(() -> task.doExecute()).doesNotThrowAnyException();

            verify(costRollUpService, times(2)).rollup(anyLong());
        }
    }
}
