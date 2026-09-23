package com.zwinsight.dashboard.task;

import com.zwinsight.dashboard.service.RiskScanService;
import com.zwinsight.security.service.TenantTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 风险扫描定时任务（V2026_59，驾驶舱 2B）
 * <p>每日 02:00 全量扫描（错开 01:15 滚动资金预测刷新——资金缺口规则消费其快照，
 * 必须在其后执行）。逐租户上下文执行，单租户失败不中断其他租户。</p>
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
            try {
                riskScanService.scan();
            } catch (Exception e) {
                log.error("租户风险扫描失败, tenantId={}", tenantId, e);
            }
        });
    }
}
