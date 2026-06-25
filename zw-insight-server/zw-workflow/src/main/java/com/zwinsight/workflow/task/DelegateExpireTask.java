package com.zwinsight.workflow.task;

import com.zwinsight.workflow.service.DelegateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 委托过期清理定时任务
 * <p>
 * 定期扫描已过期但状态仍为 ACTIVE 的委托配置，将其标记为 EXPIRED。
 * 委托结束后，新任务将恢复分配给原处理人。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelegateExpireTask {

    private final DelegateService delegateService;

    /**
     * 每10分钟执行一次过期委托清理
     */
    @Scheduled(fixedRate = 600000, initialDelay = 30000)
    public void expireDelegations() {
        log.debug("开始执行委托过期清理扫描...");
        try {
            int count = delegateService.expireOverdueDelegations();
            if (count > 0) {
                log.info("委托过期清理完成，本次清理数量：{}", count);
            }
        } catch (Exception e) {
            log.error("委托过期清理异常", e);
        }
    }
}
