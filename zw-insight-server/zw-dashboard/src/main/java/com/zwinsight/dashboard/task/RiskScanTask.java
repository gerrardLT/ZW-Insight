package com.zwinsight.dashboard.task;

import com.zwinsight.dashboard.service.RiskScanService;
import com.zwinsight.security.service.TenantTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * 风险扫描定时任务（V2026_59，驾驶舱 2B）
 * <p>每日 02:00 全量扫描（错开 01:15 滚动资金预测刷新——资金缺口规则消费其快照，
 * 必须在其后执行）。逐租户上下文执行，单租户失败不中断其他租户。</p>
 * <p><b>不吞异常（2026-09-24 修正）</b>：原实现在本层 catch 后仅 log，
 * 导致 {@code TenantTaskRunner} 把失败租户也计入「成功」（线上实证：
 * 3/7 规则因数据权限异常失败，任务却报「成功 2/2」）。现异常直接传播给
 * Runner 计入失败；并对「部分规则失败」单独 ERROR 告警，避免规则级静默失效。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskScanTask {

    private final RiskScanService riskScanService;
    private final TenantTaskRunner tenantTaskRunner;

    @Scheduled(cron = "0 0 2 * * ?")
    public void execute() {
        log.info("风险扫描定时任务开始执行");
        tenantTaskRunner.runForActiveTenants("风险扫描", tenantId -> {
            Map<String, Object> result = riskScanService.scan();
            Object failedRules = result.get("failedRules");
            if (failedRules instanceof Collection<?> c && !c.isEmpty()) {
                // 规则级失败不会让 scan() 抛异常（单规则隔离设计），必须在此显式告警
                log.error("风险扫描存在规则执行失败, tenantId={}, failedRules={}, 本轮跳过自动关闭的类型={}",
                        tenantId, failedRules, result.get("skippedAutoCloseTypes"));
            }
        });
    }
}
