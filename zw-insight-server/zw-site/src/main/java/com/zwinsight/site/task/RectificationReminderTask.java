package com.zwinsight.site.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.ReminderNotifyEvent;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizReminderConfig;
import com.zwinsight.site.domain.BizReminderLog;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.BizReminderConfigMapper;
import com.zwinsight.site.mapper.BizReminderLogMapper;
import com.zwinsight.site.service.ReminderConfigService;
import com.zwinsight.site.service.ReminderDeduplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 整改超期催办定时任务
 * <p>
 * 每日 08:00 执行，扫描超期未完成的整改记录，通过站内消息发送催办通知。
 * 支持分级升级（通知项目经理）、频率控制（intervalDays）和长期超期停止催办。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RectificationReminderTask {

    private final BizInspectionMapper inspectionMapper;
    private final BizReminderConfigMapper reminderConfigMapper;
    private final BizReminderLogMapper reminderLogMapper;
    private final BizProjectMapper projectMapper;
    private final BizProjectMemberMapper projectMemberMapper;
    private final ReminderConfigService reminderConfigService;
    private final ReminderDeduplicationService deduplicationService;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate stringRedisTemplate;

    /** 分布式锁 Key */
    private static final String LOCK_KEY = "rectification:reminder:lock";

    /** 分布式锁 TTL（30分钟） */
    private static final long LOCK_TTL_MINUTES = 30;

    /** 检查类型映射 */
    private static final String INSPECTION_TYPE_QUALITY = "QUALITY";
    private static final String INSPECTION_TYPE_SAFETY = "SAFETY";

    /**
     * 每日 08:00 执行超期催办扫描
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void execute() {
        log.info("整改超期催办定时任务开始执行");

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

        // 2. 加载催办配置（使用默认租户ID=1，多租户场景需遍历所有租户）
        // 由于定时任务无用户上下文，当前仅支持单租户或遍历所有配置
        List<BizReminderConfig> configs = loadAllEnabledConfigs();

        if (configs.isEmpty()) {
            log.info("无启用的催办配置，跳过执行");
            return;
        }

        int totalProcessed = 0;
        int totalSent = 0;

        for (BizReminderConfig config : configs) {
            if (!Boolean.TRUE.equals(config.getEnabled())) {
                log.debug("租户[{}]催办未启用，跳过", config.getTenantId());
                continue;
            }

            // 3. 查询该租户下超期记录
            List<BizInspection> overdueRecords = queryOverdueRecords(config.getTenantId(), today);

            if (overdueRecords.isEmpty()) {
                log.debug("租户[{}]无超期整改记录", config.getTenantId());
                continue;
            }

            log.info("租户[{}]发现{}条超期整改记录", config.getTenantId(), overdueRecords.size());

            // 4. 逐条处理
            for (BizInspection record : overdueRecords) {
                try {
                    boolean sent = processRecord(record, config, today);
                    totalProcessed++;
                    if (sent) {
                        totalSent++;
                    }
                } catch (Exception e) {
                    log.error("处理超期催办异常, inspectionId={}", record.getId(), e);
                    totalProcessed++;
                }
            }
        }

        log.info("整改超期催办任务完成, 处理{}条, 发送通知{}条", totalProcessed, totalSent);
    }

    /**
     * 处理单条超期记录
     *
     * @param record 检查记录
     * @param config 催办配置
     * @param today  当前日期
     * @return true-发送了通知, false-跳过
     */
    boolean processRecord(BizInspection record, BizReminderConfig config, LocalDate today) {
        // 计算超期天数
        long overdueDays = calculateOverdueDays(record.getRectificationDeadline(), today);

        // 长期超期检查：超过 longOverdueDays 停止催办
        if (overdueDays > config.getLongOverdueDays()) {
            log.debug("检查记录[{}]超期{}天，超过长期阈值{}天，跳过催办",
                    record.getId(), overdueDays, config.getLongOverdueDays());
            return false;
        }

        // 频率控制检查
        if (!deduplicationService.shouldSend(record.getId(), today, config.getIntervalDays())) {
            log.debug("检查记录[{}]未达催办间隔，跳过", record.getId());
            return false;
        }

        // 发送普通催办通知给责任人
        boolean normalSent = sendReminderNotification(record, (int) overdueDays);

        // 记录普通催办日志
        saveReminderLog(record, record.getResponsiblePersonId(), "NORMAL",
                normalSent ? "SENT" : "FAILED", (int) overdueDays);

        // 升级通知：超期天数 >= escalationDays 时通知项目经理
        if (overdueDays >= config.getEscalationDays()) {
            sendEscalationNotification(record, (int) overdueDays);
        }

        // 标记已发送
        if (normalSent) {
            deduplicationService.markSent(record.getId(), today);
        }

        return normalSent;
    }

    /**
     * 计算超期天数
     */
    long calculateOverdueDays(LocalDate deadline, LocalDate today) {
        return ChronoUnit.DAYS.between(deadline, today);
    }

    /**
     * 发送普通催办通知给整改责任人
     */
    private boolean sendReminderNotification(BizInspection record, int overdueDays) {
        try {
            String projectName = getProjectName(record.getProjectId());
            String inspectionTypeLabel = getInspectionTypeLabel(record.getInspectionType());
            String title = "整改超期催办通知";
            String content = buildReminderContent(projectName, inspectionTypeLabel,
                    record.getProblemDescription(), record.getRectificationDeadline(), overdueDays);

            // 通过 Spring Event 发送消息
            eventPublisher.publishEvent(new ReminderNotifyEvent(
                    this,
                    record.getResponsiblePersonId(),
                    title,
                    content,
                    "REMINDER",
                    "RECTIFICATION",
                    record.getId()
            ));

            return true;
        } catch (Exception e) {
            log.error("发送催办通知失败, inspectionId={}, responsiblePersonId={}",
                    record.getId(), record.getResponsiblePersonId(), e);
            return false;
        }
    }

    /**
     * 发送升级通知给项目经理
     */
    private void sendEscalationNotification(BizInspection record, int overdueDays) {
        try {
            // 查询项目经理
            List<Long> managerIds = getProjectManagerIds(record.getProjectId());
            if (managerIds.isEmpty()) {
                log.warn("项目[{}]未找到项目经理，跳过升级通知", record.getProjectId());
                return;
            }

            String projectName = getProjectName(record.getProjectId());
            String inspectionTypeLabel = getInspectionTypeLabel(record.getInspectionType());
            String title = "整改超期升级通知";
            String content = buildEscalationContent(projectName, inspectionTypeLabel,
                    record.getProblemDescription(), record.getRectificationDeadline(), overdueDays);

            for (Long managerId : managerIds) {
                try {
                    eventPublisher.publishEvent(new ReminderNotifyEvent(
                            this,
                            managerId,
                            title,
                            content,
                            "ESCALATION",
                            "RECTIFICATION",
                            record.getId()
                    ));

                    // 记录升级催办日志
                    saveReminderLog(record, managerId, "ESCALATED", "SENT", overdueDays);
                } catch (Exception e) {
                    log.error("发送升级通知失败, inspectionId={}, managerId={}",
                            record.getId(), managerId, e);
                    saveReminderLog(record, managerId, "ESCALATED", "FAILED", overdueDays);
                }
            }
        } catch (Exception e) {
            log.error("升级通知处理异常, inspectionId={}", record.getId(), e);
        }
    }

    /**
     * 构建催办消息内容
     * <p>包含：项目名称、检查类型（质量/安全）、问题描述、整改期限、已超期天数</p>
     */
    String buildReminderContent(String projectName, String inspectionType,
                                String problemDescription, LocalDate deadline, int overdueDays) {
        return String.format(
                "【整改催办】项目【%s】的%s检查中发现问题：%s。整改期限：%s，已超期%d天，请尽快处理。",
                projectName, inspectionType,
                problemDescription != null ? problemDescription : "未描述",
                deadline, overdueDays
        );
    }

    /**
     * 构建升级通知内容
     */
    String buildEscalationContent(String projectName, String inspectionType,
                                   String problemDescription, LocalDate deadline, int overdueDays) {
        return String.format(
                "【整改超期升级】项目【%s】的%s检查问题已超期%d天未完成整改：%s。整改期限：%s，请关注并督促处理。",
                projectName, inspectionType, overdueDays,
                problemDescription != null ? problemDescription : "未描述",
                deadline
        );
    }

    /**
     * 查询超期记录：rectificationStatus = PENDING 且 rectificationDeadline < today
     */
    List<BizInspection> queryOverdueRecords(Long tenantId, LocalDate today) {
        LambdaQueryWrapper<BizInspection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizInspection::getTenantId, tenantId)
                .eq(BizInspection::getRectificationStatus, "PENDING")
                .lt(BizInspection::getRectificationDeadline, today);
        return inspectionMapper.selectList(wrapper);
    }

    /**
     * 加载所有已启用的催办配置
     * <p>定时任务无用户上下文，需遍历所有租户的配置</p>
     */
    private List<BizReminderConfig> loadAllEnabledConfigs() {
        LambdaQueryWrapper<BizReminderConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizReminderConfig::getEnabled, true);
        List<BizReminderConfig> configs = reminderConfigMapper.selectList(wrapper);

        // 如果没有配置，使用默认配置（租户ID=1）
        if (configs.isEmpty()) {
            BizReminderConfig defaultConfig = reminderConfigService.getConfig(1L);
            if (defaultConfig != null && Boolean.TRUE.equals(defaultConfig.getEnabled())) {
                return List.of(defaultConfig);
            }
        }
        return configs;
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
     * 获取检查类型中文标签
     */
    String getInspectionTypeLabel(String inspectionType) {
        if (INSPECTION_TYPE_QUALITY.equals(inspectionType)) {
            return "质量";
        } else if (INSPECTION_TYPE_SAFETY.equals(inspectionType)) {
            return "安全";
        }
        return inspectionType != null ? inspectionType : "未知";
    }

    /**
     * 获取项目经理用户ID列表
     */
    private List<Long> getProjectManagerIds(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        LambdaQueryWrapper<BizProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMember::getProjectId, projectId)
                .eq(BizProjectMember::getStatus, 1)
                .apply("JSON_CONTAINS(project_roles, '\"PROJECT_MANAGER\"')");
        List<BizProjectMember> managers = projectMemberMapper.selectList(wrapper);
        return managers.stream()
                .map(BizProjectMember::getUserId)
                .toList();
    }

    /**
     * 保存催办日志
     */
    private void saveReminderLog(BizInspection record, Long receiverId,
                                 String reminderLevel, String sendStatus, int overdueDays) {
        try {
            BizReminderLog reminderLog = new BizReminderLog();
            reminderLog.setTenantId(record.getTenantId());
            reminderLog.setInspectionId(record.getId());
            reminderLog.setReceiverId(receiverId);
            reminderLog.setReminderLevel(reminderLevel);
            reminderLog.setSendStatus(sendStatus);
            reminderLog.setOverdueDays(overdueDays);
            if ("SENT".equals(sendStatus)) {
                reminderLog.setSentAt(LocalDateTime.now());
            }
            reminderLogMapper.insert(reminderLog);
        } catch (Exception e) {
            log.error("保存催办日志异常, inspectionId={}, receiverId={}", record.getId(), receiverId, e);
        }
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
