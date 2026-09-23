package com.zwinsight.dashboard.task;

import com.zwinsight.dashboard.service.ProfitSnapshotService;
import com.zwinsight.security.service.TenantTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 预计利润快照任务（V2026_59，驾驶舱 2A）
 * <p>每日 02:30 刷新当月快照（同月覆盖 upsert，保证驾驶舱「预计利润」卡不过期）。
 * 月度基线无需单独任务：上月最后一天的每日刷新即固化上月快照，
 * 趋势与归因（attributeProfitChange）按月读取历史快照即可。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfitSnapshotTask {

    private final ProfitSnapshotService profitSnapshotService;
    private final TenantTaskRunner tenantTaskRunner;

    /**
     * 每日 03:30 刷新当月快照。
     * <p>时序依赖（不可随意提前）：
     * ① 01:15 资金预测 → 02:00 风险扫描（FundGapRiskRule 消费预测快照）；
     * ② 02:30 成本归集（zw-budget CostRollUpTask）先写完 biz_cost_account 的 actual，
     *    快照的成本侧取自 CBS，必须等归集完成——曾与归集任务同在 02:30，
     *    会读到归集中的中间值导致快照抖动、月度趋势产生假断点（2026-09-23 审查修正）。</p>
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void execute() {
        log.info("预计利润快照任务开始执行");
        tenantTaskRunner.runForActiveTenants("预计利润快照", tenantId -> {
            // 不吞异常：快照生成失败必须传播到 TenantTaskRunner 计入失败，
            // 否则任务层报「成功 N/N」而快照实际未生成（2026-09-24 同类缺陷修正）
            int count = profitSnapshotService.generateSnapshot();
            log.info("租户 {} 预计利润快照完成, 项目数={}", tenantId, count);
        });
    }
}
