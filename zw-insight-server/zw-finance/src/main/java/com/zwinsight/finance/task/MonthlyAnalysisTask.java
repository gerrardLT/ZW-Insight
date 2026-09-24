package com.zwinsight.finance.task;

import com.zwinsight.finance.service.MonthlyAnalysisService;
import com.zwinsight.security.service.TenantTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 月度经营分析表生成任务（V2026_67，资金流转流程 §9「每月必须形成一个项目经营表」）
 * <p><b>执行时点：每月 1 日 04:00 生成上月</b>。选择 04:00 是为错开既有任务链——
 * 01:15 资金滚动预测（FundForecastTask）、02:00 风险扫描（RiskScanTask）、
 * 02:30 成本归集（CostRollUpTask）、03:30 利润快照（ProfitSnapshotTask）。
 * 本表的「累计发生」取 CBS {@code actual_amount}，而该列由 02:30 的成本归集任务对账，
 * 故必须排在其后，否则会拿到归集前的旧值。</p>
 * <p>幂等：同项目同月重跑为覆盖更新（唯一键 tenant+project+month+category），
 * 手工补跑不会重复插行。</p>
 * <p>租户上下文由 {@link TenantTaskRunner} 逐租户设置并标记系统任务
 * （{@code SecurityContextHolder.markSystemTask()}），否则数据权限拦截器会因缺 userId 抛异常，
 * 导致任务「执行成功但一行未写」的空转（2026-09-24 已修复的同类缺陷）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyAnalysisTask {

    private static final String LOCK_KEY = "task:monthly-analysis:lock";
    private static final long LOCK_TTL_MINUTES = 30;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MonthlyAnalysisService monthlyAnalysisService;
    private final StringRedisTemplate stringRedisTemplate;
    private final TenantTaskRunner tenantTaskRunner;

    /**
     * 每月 1 日 04:00 生成上月分析表（可通过 {@code task.monthly-analysis.cron} 覆盖）。
     */
    @Scheduled(cron = "${task.monthly-analysis.cron:0 0 4 1 * ?}")
    public void execute() {
        String lastMonth = YearMonth.now().minusMonths(1).format(MONTH_FMT);
        log.info("月度经营分析任务开始执行，目标月份={}", lastMonth);

        if (!acquireLock()) {
            log.info("月度经营分析未获取到分布式锁，跳过本轮（可能已有其他实例在执行）");
            return;
        }
        try {
            tenantTaskRunner.runForActiveTenants("月度经营分析", tenantId -> doExecute(lastMonth));
        } finally {
            releaseLock();
        }
    }

    /**
     * 单租户内的生成逻辑（已在租户上下文与系统任务标记中）。
     * <p>失败不吞：{@code runForActiveTenants} 会捕获并计数，全部租户失败时升级为 ERROR，
     * 避免「日志报成功、实际零行写入」的静默空转。</p>
     */
    void doExecute(String month) {
        Map<String, Object> report = monthlyAnalysisService.generateAll(month);
        // 经 Number 取整数：直接 (int) 强转 Object 在值类型不符时报错信息隐晦
        int projectCount = ((Number) report.getOrDefault("projectCount", 0)).intValue();
        int successCount = ((Number) report.getOrDefault("successCount", 0)).intValue();
        Object failed = report.get("failedProjects");
        if (projectCount == 0) {
            // 无 CBS 账户的项目 → 本表无数据可生成。这是数据现状，不是任务故障，
            // 但必须 WARN 出来（否则「本月经营表为空」会被误认为任务未跑）
            log.warn("本租户无已建 CBS 成本账户的项目，月度经营分析表 {} 无数据可生成", month);
        } else if (successCount < projectCount) {
            log.error("月度经营分析表 {} 生成不完整：成功 {}/{} 个项目，失败明细={}",
                    month, successCount, projectCount, failed);
        } else {
            log.info("月度经营分析表 {} 生成完成：{} 个项目", month, successCount);
        }
    }

    private boolean acquireLock() {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "locked", LOCK_TTL_MINUTES, TimeUnit.MINUTES);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            // Redis 不可用时选择「不执行」而非「裸跑」：多实例并发生成虽有唯一键兜底不会记错账，
            // 但会产生大量无谓的 DB 压力与重复覆盖更新
            log.error("获取月度经营分析分布式锁异常，本轮跳过", e);
            return false;
        }
    }

    private void releaseLock() {
        try {
            stringRedisTemplate.delete(LOCK_KEY);
        } catch (Exception e) {
            // 释放失败不影响业务：锁有 TTL 会自然过期，下一轮仍可获取
            log.warn("释放月度经营分析分布式锁失败（{} 分钟后自动过期）", LOCK_TTL_MINUTES, e);
        }
    }
}
