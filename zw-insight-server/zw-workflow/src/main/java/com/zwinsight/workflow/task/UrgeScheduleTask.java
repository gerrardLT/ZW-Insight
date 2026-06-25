package com.zwinsight.workflow.task;

import com.zwinsight.workflow.service.UrgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 催办定时任务 - 定期扫描超时待办并自动发送催办通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrgeScheduleTask {

    private final UrgeService urgeService;

    /**
     * 每30分钟执行一次自动催办扫描
     */
    @Scheduled(fixedRate = 1800000, initialDelay = 60000)
    public void autoUrge() {
        log.info("开始执行自动催办扫描...");
        try {
            int count = urgeService.autoUrge();
            log.info("自动催办扫描完成，本次催办任务数：{}", count);
        } catch (Exception e) {
            log.error("自动催办扫描异常", e);
        }
    }
}
