package com.zwinsight.workflow.task;

import com.zwinsight.security.service.TenantTaskRunner;
import com.zwinsight.workflow.service.UrgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 催办定时任务 - 定期扫描超时待办并自动发送催办通知
 * <p><b>逐租户执行（2026-09-25 修复）</b>：原实现在无租户上下文的 scheduling 线程
 * 直接扫描，wf_urge_record 的 INSERT 被写防护拒绝（IllegalStateException:
 * 租户上下文缺失），自动催办自上线起从未生效（线上实证：每 30 分钟一条
 * 「自动催办扫描异常」ERROR）。现按 RiskScanTask 同款模式由
 * {@link TenantTaskRunner} 逐租户设置上下文，{@code UrgeService.autoUrge}
 * 内以 taskTenantId 过滤任务防跨租户错乱。</p>
 * <p><b>不吞异常</b>：原 try/catch 会把失败租户计入「成功」，异常直接传播给
 * Runner 做失败统计与醒目告警。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrgeScheduleTask {

    private final UrgeService urgeService;
    private final TenantTaskRunner tenantTaskRunner;

    /**
     * 每30分钟执行一次自动催办扫描（逐租户）
     */
    @Scheduled(fixedRate = 1800000, initialDelay = 60000)
    public void autoUrge() {
        log.info("开始执行自动催办扫描...");
        tenantTaskRunner.runForActiveTenants("自动催办", tenantId -> {
            int count = urgeService.autoUrge(tenantId);
            log.info("自动催办扫描完成, tenantId={}, 本次催办任务数={}", tenantId, count);
        });
    }
}
