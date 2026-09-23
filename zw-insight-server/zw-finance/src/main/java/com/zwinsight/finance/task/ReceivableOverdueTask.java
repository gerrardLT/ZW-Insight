package com.zwinsight.finance.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.UrgeNotifyEvent;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.mapper.BizReceivableMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.security.service.TenantTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 应收逾期催办定时任务（V2026_57）
 * <p>
 * 每日 08:30 执行，扫描 OPEN 应收台账：7 天内到期发 UPCOMING/URGENT 提醒（同级别只发一次），
 * 已逾期发 OVERDUE 催办（每 3 天一次，逾期超 180 天停止催办）。
 * 通知经 {@link UrgeNotifyEvent} 走真实站内信链路（zw-message UrgeNotifyEventListener 消费），
 * 接收人为项目的 PROJECT_MANAGER 成员；无项目经理时记录日志不静默伪造收件人。
 * 范式对齐 {@link RetentionWarningTask}（Redis 去重 key + 催办频控）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceivableOverdueTask {

    private final BizReceivableMapper receivableMapper;
    private final BizProjectMapper projectMapper;
    private final BizProjectMemberMapper projectMemberMapper;
    private final RedisUtils redisUtils;
    private final TenantTaskRunner tenantTaskRunner;
    private final ApplicationEventPublisher eventPublisher;

    /** 去重 key 前缀：receivable:warned:{receivableId}:{level} */
    private static final String WARNED_KEY_PREFIX = "receivable:warned:";

    /** 逾期催办频率 key 前缀：receivable:overdue:last:{receivableId} */
    private static final String OVERDUE_LAST_PREFIX = "receivable:overdue:last:";

    /** 逾期超过此天数停止催办（与质保金催办口径一致） */
    private static final int LONG_OVERDUE_DAYS = 180;

    /** 逾期催办间隔天数 */
    private static final int OVERDUE_REMINDER_INTERVAL_DAYS = 3;

    /** 非逾期去重 key 过期时间（30天） */
    private static final long WARNED_KEY_EXPIRE_SECONDS = 30L * 24 * 60 * 60;

    /** 预警级别常量 */
    public static final String LEVEL_UPCOMING = "UPCOMING";
    public static final String LEVEL_URGENT = "URGENT";
    public static final String LEVEL_OVERDUE = "OVERDUE";

    /**
     * 每日 08:30 执行应收逾期扫描（错开质保金预警 08:00）
     */
    @Scheduled(cron = "0 30 8 * * ?")
    public void execute() {
        log.info("应收逾期催办定时任务开始执行");
        tenantTaskRunner.runForActiveTenants("应收逾期催办", tenantId -> doExecute());
    }

    void doExecute() {
        LocalDate today = LocalDate.now();
        List<BizReceivable> records = receivableMapper.selectList(new LambdaQueryWrapper<BizReceivable>()
                .eq(BizReceivable::getStatus, BizReceivable.STATUS_OPEN)
                .le(BizReceivable::getDueDate, today.plusDays(30)));

        int processedCount = 0;
        int warningCount = 0;
        for (BizReceivable record : records) {
            try {
                BigDecimal balance = openBalance(record);
                if (balance.signum() <= 0) {
                    continue;
                }
                processedCount++;
                String level = processRecord(record, today);
                if (level == null || !shouldSendNotification(record.getId(), level, today)) {
                    continue;
                }
                boolean success = sendWarning(record, balance, level);
                if (success) {
                    markAsSent(record.getId(), level, today);
                    warningCount++;
                }
            } catch (Exception e) {
                log.error("处理应收逾期催办异常, receivableId={}", record.getId(), e);
            }
        }
        log.info("应收逾期催办任务完成, 处理{}条, 发送通知{}条", processedCount, warningCount);
    }

    /**
     * 判定预警级别（到期日剩余天数分级，口径对齐 RetentionWarningTask.processRecord）
     *
     * @return 预警级别；不需要发通知时返回 null
     */
    public String processRecord(BizReceivable record, LocalDate today) {
        long daysUntilDue = ChronoUnit.DAYS.between(today, record.getDueDate());
        if (daysUntilDue < 0) {
            if (Math.abs(daysUntilDue) > LONG_OVERDUE_DAYS) {
                log.info("应收 {} 逾期超{}天，停止催办", record.getId(), LONG_OVERDUE_DAYS);
                return null;
            }
            return LEVEL_OVERDUE;
        }
        if (daysUntilDue == 0) {
            return LEVEL_OVERDUE;
        }
        if (daysUntilDue <= 7) {
            return LEVEL_URGENT;
        }
        return LEVEL_UPCOMING;
    }

    /**
     * 去重/催办频控检查（非逾期同级别只发一次；逾期每 3 天一次）
     */
    public boolean shouldSendNotification(Long receivableId, String level, LocalDate today) {
        if (LEVEL_OVERDUE.equals(level)) {
            return shouldSendOverdueReminder(receivableId, today);
        }
        return !Boolean.TRUE.equals(redisUtils.hasKey(buildWarnedKey(receivableId, level)));
    }

    private boolean shouldSendOverdueReminder(Long receivableId, LocalDate today) {
        Object lastSentValue = redisUtils.get(buildOverdueLastKey(receivableId));
        if (lastSentValue == null) {
            return true;
        }
        try {
            LocalDate lastSentDate = LocalDate.parse(lastSentValue.toString());
            return ChronoUnit.DAYS.between(lastSentDate, today) >= OVERDUE_REMINDER_INTERVAL_DAYS;
        } catch (Exception e) {
            log.warn("解析应收催办日期异常, receivableId={}, value={}", receivableId, lastSentValue, e);
            return true;
        }
    }

    /**
     * 发送催办通知（真实站内信链路：UrgeNotifyEvent → zw-message 监听器）
     *
     * @return 是否成功发出（无项目经理收件人时返回 false，不静默伪造）
     */
    public boolean sendWarning(BizReceivable record, BigDecimal balance, String level) {
        BizProject project = projectMapper.selectById(record.getProjectId());
        String projectName = project != null ? project.getProjectName() : "未知项目";
        List<Long> managerIds = findProjectManagerIds(record.getProjectId());
        if (managerIds.isEmpty()) {
            log.warn("应收催办无收件人：项目 {} 未配置 PROJECT_MANAGER 成员, receivableId={}",
                    record.getProjectId(), record.getId());
            return false;
        }
        String title = switch (level) {
            case LEVEL_UPCOMING -> "应收款即将到期提醒";
            case LEVEL_URGENT -> "应收款紧急到期提醒";
            default -> "应收款逾期催办提醒";
        };
        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(record.getDueDate(), LocalDate.now()));
        String content = String.format(
                "项目【%s】应收款（余额 %s 元，到期日 %s）%s，请及时跟进回款。",
                projectName, balance.toPlainString(), record.getDueDate(),
                overdueDays > 0 ? "已逾期 " + overdueDays + " 天" : "即将到期");
        for (Long managerId : managerIds) {
            eventPublisher.publishEvent(new UrgeNotifyEvent(this, managerId, title, content, null, null));
        }
        return true;
    }

    /**
     * 通知发送成功后标记去重/催办记录
     */
    public void markAsSent(Long receivableId, String level, LocalDate today) {
        if (LEVEL_OVERDUE.equals(level)) {
            redisUtils.set(buildOverdueLastKey(receivableId), today.toString());
        } else {
            redisUtils.set(buildWarnedKey(receivableId, level), "1", WARNED_KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 应收结清时清除全部去重 key（由核销链路调用，防台账已清仍收到催办）
     */
    public void onReceivableClosed(Long receivableId) {
        redisUtils.delete(buildWarnedKey(receivableId, LEVEL_UPCOMING));
        redisUtils.delete(buildWarnedKey(receivableId, LEVEL_URGENT));
        redisUtils.delete(buildOverdueLastKey(receivableId));
    }

    /**
     * 查询项目的 PROJECT_MANAGER 成员用户ID（状态正常）
     */
    private List<Long> findProjectManagerIds(Long projectId) {
        List<BizProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<BizProjectMember>()
                        .eq(BizProjectMember::getProjectId, projectId)
                        .eq(BizProjectMember::getStatus, 1)
                        .like(BizProjectMember::getProjectRoles, "PROJECT_MANAGER"));
        return members.stream().map(BizProjectMember::getUserId).distinct().toList();
    }

    private BigDecimal openBalance(BizReceivable record) {
        BigDecimal amount = record.getReceivableAmount() != null ? record.getReceivableAmount() : BigDecimal.ZERO;
        BigDecimal written = record.getWrittenOffAmount() != null ? record.getWrittenOffAmount() : BigDecimal.ZERO;
        return amount.subtract(written);
    }

    public String buildWarnedKey(Long receivableId, String level) {
        return WARNED_KEY_PREFIX + receivableId + ":" + level;
    }

    public String buildOverdueLastKey(Long receivableId) {
        return OVERDUE_LAST_PREFIX + receivableId;
    }
}
