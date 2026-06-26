package com.zwinsight.contract.task;

import com.zwinsight.contract.dto.ContractExpiryDTO;
import com.zwinsight.contract.service.ContractExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 合同到期提醒定时任务
 * <p>
 * 每日 09:00 执行，扫描即将到期的合同（30天内），
 * 根据剩余天数分级（UPCOMING/URGENT）发送站内消息通知合同负责人。
 * 使用 Redis 分布式锁确保集群环境只执行一次。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExpiryTask {

    private final ContractExpiryService expiryService;
    private final StringRedisTemplate stringRedisTemplate;

    /** 分布式锁 Key */
    private static final String LOCK_KEY = "task:contract-expiry:lock";

    /** 分布式锁 TTL（30分钟） */
    private static final long LOCK_TTL_MINUTES = 30;

    /**
     * 每日 09:00 执行合同到期扫描
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void execute() {
        log.info("合同到期提醒定时任务开始执行");

        // 1. 获取分布式锁，防止多实例并发执行
        if (!acquireLock()) {
            log.info("获取分布式锁失败，跳过本次执行（可能已有其他实例在执行）");
            return;
        }

        try {
            doExecute();
        } finally {
            releaseLock();
        }
    }

    /**
     * 核心执行逻辑（获取锁之后）
     */
    void doExecute() {
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysLater = today.plusDays(30);

        // 2. 查询所有即将到期的合同（所有类型：采购/分包/机械/劳务）
        List<ContractExpiryDTO> contracts = expiryService.queryExpiringContracts(today, thirtyDaysLater);

        if (contracts.isEmpty()) {
            log.info("无即将到期的合同，跳过执行");
            return;
        }

        int processedCount = 0;
        int notifiedCount = 0;

        for (ContractExpiryDTO contract : contracts) {
            try {
                processedCount++;
                boolean notified = processContract(contract, today);
                if (notified) {
                    notifiedCount++;
                }
            } catch (Exception e) {
                log.error("处理合同到期提醒异常, contractId={}, contractCode={}",
                        contract.getId(), contract.getContractCode(), e);
            }
        }

        log.info("合同到期提醒任务完成, 处理{}条, 发送通知{}条", processedCount, notifiedCount);
    }

    /**
     * 处理单个合同的到期提醒逻辑
     *
     * @param contract 合同到期信息
     * @param today    当天日期
     * @return true表示发送了通知，false表示跳过
     */
    boolean processContract(ContractExpiryDTO contract, LocalDate today) {
        // 跳过已终止/已结算/已关闭的合同
        if (expiryService.shouldSkip(contract)) {
            return false;
        }

        // 判断通知级别
        String level = expiryService.determineLevel(contract.getEndDate(), today);
        if (level == null) {
            return false;
        }

        // Redis 去重：同一合同同一级别只提醒一次
        if (!expiryService.shouldSendNotification(contract.getId(), level)) {
            return false;
        }

        // 发送站内消息
        expiryService.sendExpiryNotification(contract, level, today);

        // 标记已发送
        expiryService.markAsSent(contract.getId(), level);

        return true;
    }

    /**
     * 获取分布式锁
     */
    private boolean acquireLock() {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "locked", LOCK_TTL_MINUTES, TimeUnit.MINUTES);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.error("获取分布式锁异常", e);
            return false;
        }
    }

    /**
     * 释放分布式锁
     */
    private void releaseLock() {
        try {
            stringRedisTemplate.delete(LOCK_KEY);
        } catch (Exception e) {
            log.error("释放分布式锁异常", e);
        }
    }
}
