package com.zwinsight.contract.service;

import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.contract.domain.BizContractExpiryLog;
import com.zwinsight.contract.dto.ContractExpiryDTO;
import com.zwinsight.contract.mapper.BizContractExpiryLogMapper;
import com.zwinsight.contract.mapper.BizExpenseContractMapper;
import com.zwinsight.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 合同到期判断和通知服务
 * <p>
 * 负责判断合同到期级别、Redis去重、发送站内通知消息。
 * 被 ContractExpiryTask 定时任务调用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractExpiryService {

    private final RedisUtils redisUtils;
    private final MessageService messageService;
    private final BizExpenseContractMapper expenseContractMapper;
    private final BizContractExpiryLogMapper expiryLogMapper;

    public static final String LEVEL_UPCOMING = "UPCOMING";
    public static final String LEVEL_URGENT = "URGENT";

    private static final String WARNED_KEY_PREFIX = "contract:expiry:warned:";
    private static final long WARNED_TTL_DAYS = 90;

    /**
     * 判断通知级别
     *
     * @param endDate 合同到期日期
     * @param today   当前日期
     * @return URGENT(≤7天) / UPCOMING(≤30天) / null(不需通知)
     */
    public String determineLevel(LocalDate endDate, LocalDate today) {
        long remainingDays = ChronoUnit.DAYS.between(today, endDate);
        if (remainingDays <= 0) {
            return null; // 已过期不处理
        }
        if (remainingDays <= 7) {
            return LEVEL_URGENT;
        }
        if (remainingDays <= 30) {
            return LEVEL_UPCOMING;
        }
        return null;
    }

    /**
     * 是否应跳过合同（已终止/已结算/已关闭的合同不再提醒）
     *
     * @param contract 合同到期信息
     * @return true表示应跳过
     */
    public boolean shouldSkip(ContractExpiryDTO contract) {
        String status = contract.getStatus();
        return "CLOSED".equals(status)
                || "SETTLED".equals(status)
                || "TERMINATED".equals(status);
    }

    /**
     * Redis 去重判断：同一合同同一级别是否已发送过通知
     *
     * @param contractId 合同ID
     * @param level      通知级别
     * @return true表示应该发送通知（未发送过）
     */
    public boolean shouldSendNotification(Long contractId, String level) {
        String key = buildWarnedKey(contractId, level);
        return !Boolean.TRUE.equals(redisUtils.hasKey(key));
    }

    /**
     * Redis 标记已发送（90天过期）
     *
     * @param contractId 合同ID
     * @param level      通知级别
     */
    public void markAsSent(Long contractId, String level) {
        String key = buildWarnedKey(contractId, level);
        redisUtils.set(key, "1", WARNED_TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * 发送合同到期提醒站内消息
     * <p>
     * 消息内容包含：合同编号、合同名称、供应商/分包商名称、到期日期、剩余天数
     * </p>
     *
     * @param contract 合同到期信息
     * @param level    通知级别（URGENT/UPCOMING）
     * @param today    当前日期
     */
    public void sendExpiryNotification(ContractExpiryDTO contract, String level, LocalDate today) {
        Long userId = contract.getResponsibleUserId();
        if (userId == null) {
            log.warn("合同[{}]未设置负责人，跳过通知", contract.getContractCode());
            return;
        }

        long remainingDays = ChronoUnit.DAYS.between(today, contract.getEndDate());
        String levelDesc = LEVEL_URGENT.equals(level) ? "紧急到期" : "即将到期";

        String title = String.format("【%s】合同到期提醒", levelDesc);
        String content = buildNotificationContent(contract, remainingDays, levelDesc);

        try {
            messageService.sendMessage(
                    userId,
                    title,
                    content,
                    "WARNING",
                    "CONTRACT_EXPIRY",
                    contract.getId()
            );

            // 记录提醒日志
            saveExpiryLog(contract, level, (int) remainingDays, userId, "SENT");
            log.info("合同到期提醒已发送: contractId={}, level={}, remainingDays={}",
                    contract.getId(), level, remainingDays);
        } catch (Exception e) {
            // 记录失败日志
            saveExpiryLog(contract, level, (int) remainingDays, userId, "FAILED");
            log.error("合同到期提醒发送失败: contractId={}, error={}", contract.getId(), e.getMessage(), e);
        }
    }

    /**
     * 查询即将到期的合同（所有类型）
     * <p>
     * 联合查询 biz_expense_contract 中 end_date 在 [today, thirtyDaysLater] 范围内的合同，
     * 排除已关闭/已结算/已终止状态的合同。
     * </p>
     *
     * @param today            当前日期
     * @param thirtyDaysLater  30天后的日期
     * @return 即将到期的合同列表
     */
    public List<ContractExpiryDTO> queryExpiringContracts(LocalDate today, LocalDate thirtyDaysLater) {
        return expenseContractMapper.selectExpiringContracts(today, thirtyDaysLater);
    }

    /**
     * 构建 Redis 去重 key
     */
    private String buildWarnedKey(Long contractId, String level) {
        return WARNED_KEY_PREFIX + contractId + ":" + level;
    }

    /**
     * 构建通知消息内容
     */
    private String buildNotificationContent(ContractExpiryDTO contract, long remainingDays, String levelDesc) {
        return String.format(
                "您负责的合同%s，请及时处理。\n" +
                        "合同编号：%s\n" +
                        "合同名称：%s\n" +
                        "供应商/分包商：%s\n" +
                        "到期日期：%s\n" +
                        "剩余天数：%d天",
                levelDesc,
                contract.getContractCode(),
                contract.getContractName(),
                contract.getCounterpartName(),
                contract.getEndDate().toString(),
                remainingDays
        );
    }

    /**
     * 保存到期提醒日志
     */
    private void saveExpiryLog(ContractExpiryDTO contract, String level, int remainingDays,
                               Long notifyUserId, String notifyStatus) {
        BizContractExpiryLog logEntry = new BizContractExpiryLog();
        logEntry.setContractId(contract.getId());
        logEntry.setContractTable(contract.getContractTable());
        logEntry.setContractCode(contract.getContractCode());
        logEntry.setContractCategory(contract.getContractCategory());
        logEntry.setLevel(level);
        logEntry.setRemainingDays(remainingDays);
        logEntry.setNotifyUserId(notifyUserId);
        logEntry.setNotifyStatus(notifyStatus);
        expiryLogMapper.insert(logEntry);
    }
}
