package com.zwinsight.dashboard.task;

import com.zwinsight.dashboard.service.CockpitService;
import com.zwinsight.security.service.TenantTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 驾驶舱首屏预热（overview 首调 > 15s 性能项的根治，2026-09-25）。
 * <p>线上诊断实证（steady-state 对比首调）：服务运行 30 分钟后 overview 仅需
 * 0.15~0.3s，「首调 >15s」全部集中在重启后第一次请求——JIT 未编译热点路径 +
 * HikariCP 建连 + MyBatis 语句/结果集映射缓存冷启动，叠加聚合 4 项目 × 多表查询。
 * 属冷启动现象而非结构慢查询，故修复方式是<b>启动后台预热</b>而非改写 SQL。</p>
 * <p>实现约束：
 * <ul>
 *   <li>逐租户上下文执行（TenantTaskRunner）——getOverview 链路依赖租户拦截器，
 *       无上下文只会预热到 tenant_id=0 的空查询计划，等于白热；</li>
 *   <li>异步执行不阻塞就绪探针；预热失败仅 WARN（下一请求自行承担冷启动），
 *       不影响启动成功判定——这是预热语义的合理边界，非业务静默失败；</li>
 *   <li>结果不缓存不落库，仅走真实查询路径产生缓存/JIT/连接副作用。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CockpitWarmupListener {

    private final CockpitService cockpitService;
    private final TenantTaskRunner tenantTaskRunner;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmupOnStartup() {
        long start = System.currentTimeMillis();
        try {
            tenantTaskRunner.runForActiveTenants("驾驶舱预热", tenantId -> {
                long t0 = System.currentTimeMillis();
                // 走首页真实依赖链：8 卡聚合 + 项目健康度（含逐项目预计利润计算）
                cockpitService.getOverview(null, null);
                cockpitService.getProjectHealth(null, null, null);
                log.info("驾驶舱预热完成, tenantId={}, 耗时={}ms", tenantId,
                        System.currentTimeMillis() - t0);
            });
        } catch (Exception e) {
            // 预热尽力而为：失败不阻断（应用已就绪），醒目 WARN 留痕
            log.warn("驾驶舱预热异常（首个真实请求将承担冷启动开销）", e);
        } finally {
            log.info("驾驶舱预热任务结束，总耗时={}ms", System.currentTimeMillis() - start);
        }
    }
}
