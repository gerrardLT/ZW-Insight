package com.zwinsight.finance.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.domain.BizRetentionWarningLog;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import com.zwinsight.finance.mapper.BizRetentionWarningLogMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 质保金预警定时任务
 * <p>
 * 每日 08:00 执行，扫描未退还的质保金记录，按到期日剩余天数进行分级预警。
 * 包含通知去重（非逾期同级别只发一次）和逾期催办频率控制（每3天一次）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionWarningTask {

    private final BizRetentionMoneyMapper retentionMoneyMapper;
    private final BizRetentionWarningLogMapper warningLogMapper;
    private final BizProjectMapper projectMapper;
    private final BizConstructionContractMapper contractMapper;
    private final RedisUtils redisUtils;

    /** 去重 key 前缀：retention:warned:{retentionId}:{level} */
    private static final String WARNED_KEY_PREFIX = "retention:warned:";

    /** 逾期催办频率 key 前缀：retention:overdue:last:{retentionId} */
    private static final String OVERDUE_LAST_PREFIX = "retention:overdue:last:";

    /** 逾期超过此天数停止催办 */
    private static final int LONG_OVERDUE_DAYS = 180;

    /** 逾期催办间隔天数 */
    private static final int OVERDUE_REMINDER_INTERVAL_DAYS = 3;

    /** 非逾期去重 key 过期时间（30天） */
    private static final long WARNED_KEY_EXPIRE_SECONDS = 30L * 24 * 60 * 60;

    /** 预警级别常量 */
    public static final String LEVEL_UPCOMING = "UPCOMING";
    public static final String LEVEL_URGENT = "URGENT";
    public static final String LEVEL_OVERDUE = "OVERDUE";
    public static final String LEVEL_LONG_OVERDUE = "LONG_OVERDUE";

    /**
     * 每日 08:00 执行质保金预警扫描
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void execute() {
        log.info("质保金预警定时任务开始执行");
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysLater = today.plusDays(30);

        // 1. 查询需要预警的质保金记录：status != 'RETURNED' 且到期日在30天内或已过期
        List<BizRetentionMoney> records = queryPendingRecords(thirtyDaysLater);

        int processedCount = 0;
        int warningCount = 0;

        for (BizRetentionMoney record : records) {
            try {
                processedCount++;
                String level = processRecord(record, today);
                if (level == null) {
                    continue;
                }

                // 去重/催办检查
                if (!shouldSendNotification(record.getId(), level, today)) {
                    continue;
                }

                // 发送通知
                boolean success = sendWarning(record, level);

                // 记录去重/催办标记 + 预警日志
                if (success) {
                    markAsSent(record.getId(), level, today);
                    saveWarningLog(record.getId(), level, "SENT");
                    warningCount++;
                } else {
                    saveWarningLog(record.getId(), level, "FAILED");
                }
            } catch (Exception e) {
                log.error("处理质保金预警异常, retentionId={}", record.getId(), e);
            }
        }

        log.info("质保金预警任务完成, 处理{}条, 发送通知{}条", processedCount, warningCount);
    }

    /**
     * 查询待处理的质保金记录
     * <p>
     * 条件：status != 'RETURNED' 且 expireDate <= 今天+30天（即到期日在30天内或已过期）
     * </p>
     */
    private List<BizRetentionMoney> queryPendingRecords(LocalDate thirtyDaysLater) {
        LambdaQueryWrapper<BizRetentionMoney> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ne(BizRetentionMoney::getStatus, "RETURNED")
                .le(BizRetentionMoney::getExpireDate, thirtyDaysLater);
        return retentionMoneyMapper.selectList(queryWrapper);
    }

    /**
     * 处理单条质保金记录，判定预警级别
     *
     * @param record 质保金记录
     * @param today  当天日期
     * @return 预警级别（UPCOMING/URGENT/OVERDUE），如果不需要发通知则返回 null
     */
    public String processRecord(BizRetentionMoney record, LocalDate today) {
        long daysUntilExpire = ChronoUnit.DAYS.between(today, record.getExpireDate());

        // 分级逻辑
        if (daysUntilExpire < 0) {
            // 已逾期
            long overdueDays = Math.abs(daysUntilExpire);
            if (overdueDays > LONG_OVERDUE_DAYS) {
                // 逾期超 180 天，标记 LONG_OVERDUE，停止催办
                log.info("质保金记录 {} 逾期超{}天，标记LONG_OVERDUE停止催办",
                        record.getId(), LONG_OVERDUE_DAYS);
                return null;
            }
            return LEVEL_OVERDUE;
        } else if (daysUntilExpire >= 1 && daysUntilExpire <= 7) {
            // 7天≥剩余≥1天 → URGENT
            return LEVEL_URGENT;
        } else if (daysUntilExpire >= 8 && daysUntilExpire <= 30) {
            // 30天≥剩余≥8天 → UPCOMING
            return LEVEL_UPCOMING;
        }

        // daysUntilExpire == 0 视为当天到期，归为 OVERDUE
        if (daysUntilExpire == 0) {
            return LEVEL_OVERDUE;
        }

        return null;
    }

    /**
     * 判断是否应该发送通知（去重 + 催办频率控制）
     *
     * @param retentionId 质保金记录ID
     * @param level       预警级别
     * @param today       当天日期
     * @return true-应该发送，false-跳过
     */
    public boolean shouldSendNotification(Long retentionId, String level, LocalDate today) {
        if (LEVEL_OVERDUE.equals(level)) {
            // 逾期催办频率控制：每3天发送一次
            return shouldSendOverdueReminder(retentionId, today);
        } else {
            // 非逾期去重：同级别只发一次
            return !isAlreadyWarned(retentionId, level);
        }
    }

    /**
     * 检查非逾期记录是否已经发送过通知（去重检查）
     *
     * @param retentionId 质保金记录ID
     * @param level       预警级别（UPCOMING/URGENT）
     * @return true-已发送过，false-未发送
     */
    private boolean isAlreadyWarned(Long retentionId, String level) {
        String key = buildWarnedKey(retentionId, level);
        return Boolean.TRUE.equals(redisUtils.hasKey(key));
    }

    /**
     * 检查逾期记录是否到了催办时间
     *
     * @param retentionId 质保金记录ID
     * @param today       当天日期
     * @return true-应该发送催办，false-未到催办时间
     */
    private boolean shouldSendOverdueReminder(Long retentionId, LocalDate today) {
        String key = buildOverdueLastKey(retentionId);
        Object lastSentValue = redisUtils.get(key);
        if (lastSentValue == null) {
            // 首次催办，可以发送
            return true;
        }
        try {
            LocalDate lastSentDate = LocalDate.parse(lastSentValue.toString());
            long daysSinceLastSent = ChronoUnit.DAYS.between(lastSentDate, today);
            return daysSinceLastSent >= OVERDUE_REMINDER_INTERVAL_DAYS;
        } catch (Exception e) {
            log.warn("解析逾期催办日期异常, key={}, value={}", key, lastSentValue, e);
            // 解析异常，允许发送
            return true;
        }
    }

    /**
     * 发送预警通知
     * <p>
     * 当前使用 log.info 模拟通知发送，后续集成 MessageService 发送站内信。
     * 通知内容包含：项目名称、合同名称、质保金金额、到期日期、预警级别。
     * </p>
     *
     * @param record 质保金记录
     * @param level  预警级别
     * @return true-发送成功，false-发送失败
     */
    public boolean sendWarning(BizRetentionMoney record, String level) {
        try {
            // 查询项目名称
            String projectName = getProjectName(record.getProjectId());
            // 查询合同编号（作为合同标识）
            String contractName = getContractName(record.getContractId());

            String title = buildNotificationTitle(level);
            String content = buildNotificationContent(projectName, contractName,
                    record.getRetentionAmount(), record.getExpireDate(), level);

            // TODO: 后续集成 MessageService 发送站内信给项目负责人 + 财务人员
            // messageService.sendMessage(receiverIds, title, content, "WARNING", "RETENTION", record.getId());
            log.info("【质保金预警通知】标题: {}, 内容: {}", title, content);
            return true;
        } catch (Exception e) {
            log.error("发送质保金预警通知失败, retentionId={}, level={}", record.getId(), level, e);
            return false;
        }
    }

    /**
     * 通知发送成功后标记去重/催办记录
     *
     * @param retentionId 质保金记录ID
     * @param level       预警级别
     * @param today       当天日期
     */
    public void markAsSent(Long retentionId, String level, LocalDate today) {
        if (LEVEL_OVERDUE.equals(level)) {
            // 逾期催办：记录本次发送日期
            String key = buildOverdueLastKey(retentionId);
            redisUtils.set(key, today.toString());
        } else {
            // 非逾期去重：标记已发送（30天过期）
            String key = buildWarnedKey(retentionId, level);
            redisUtils.set(key, "1", WARNED_KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 获取项目名称
     */
    private String getProjectName(Long projectId) {
        if (projectId == null) {
            return "未知项目";
        }
        BizProject project = projectMapper.selectById(projectId);
        return project != null ? project.getProjectName() : "未知项目";
    }

    /**
     * 获取合同名称/编号
     */
    private String getContractName(Long contractId) {
        if (contractId == null) {
            return "未知合同";
        }
        BizConstructionContract contract = contractMapper.selectById(contractId);
        return contract != null ? contract.getContractCode() : "未知合同";
    }

    /**
     * 构建通知标题
     */
    private String buildNotificationTitle(String level) {
        switch (level) {
            case LEVEL_UPCOMING:
                return "质保金即将到期提醒";
            case LEVEL_URGENT:
                return "质保金紧急到期提醒";
            case LEVEL_OVERDUE:
                return "质保金逾期催办提醒";
            default:
                return "质保金预警提醒";
        }
    }

    /**
     * 构建通知内容
     */
    private String buildNotificationContent(String projectName, String contractName,
                                            java.math.BigDecimal retentionAmount,
                                            LocalDate expireDate, String level) {
        String levelDesc;
        switch (level) {
            case LEVEL_UPCOMING:
                levelDesc = "即将到期";
                break;
            case LEVEL_URGENT:
                levelDesc = "紧急到期";
                break;
            case LEVEL_OVERDUE:
                levelDesc = "已逾期未退还";
                break;
            default:
                levelDesc = level;
        }
        return String.format("项目【%s】合同【%s】的质保金（金额: %s元）%s，到期日: %s，预警级别: %s，请及时处理。",
                projectName, contractName,
                retentionAmount != null ? retentionAmount.toPlainString() : "0",
                levelDesc, expireDate, level);
    }

    /**
     * 构建去重 Redis key
     */
    public String buildWarnedKey(Long retentionId, String level) {
        return WARNED_KEY_PREFIX + retentionId + ":" + level;
    }

    /**
     * 构建逾期催办频率 Redis key
     */
    public String buildOverdueLastKey(Long retentionId) {
        return OVERDUE_LAST_PREFIX + retentionId;
    }

    /**
     * 质保金退还时清除所有相关 Redis keys（去重key + 逾期催办key）
     *
     * @param retentionId 质保金记录ID
     */
    public void onRetentionReturned(Long retentionId) {
        // 清除所有级别的去重key
        String upcomingKey = buildWarnedKey(retentionId, LEVEL_UPCOMING);
        String urgentKey = buildWarnedKey(retentionId, LEVEL_URGENT);
        String overdueKey = buildWarnedKey(retentionId, LEVEL_OVERDUE);
        String overdueLastKey = buildOverdueLastKey(retentionId);

        redisUtils.delete(upcomingKey);
        redisUtils.delete(urgentKey);
        redisUtils.delete(overdueKey);
        redisUtils.delete(overdueLastKey);

        log.info("质保金 {} 已退还，清除所有预警去重key", retentionId);
    }

    /**
     * 保存预警日志
     */
    protected void saveWarningLog(Long retentionId, String level, String notifyStatus) {
        BizRetentionWarningLog warningLog = new BizRetentionWarningLog();
        warningLog.setRetentionId(retentionId);
        warningLog.setWarningLevel(level);
        warningLog.setNotifyStatus(notifyStatus);
        warningLog.setRetryCount(0);
        if ("SENT".equals(notifyStatus)) {
            warningLog.setSentAt(LocalDateTime.now());
        }
        warningLogMapper.insert(warningLog);
    }
}
