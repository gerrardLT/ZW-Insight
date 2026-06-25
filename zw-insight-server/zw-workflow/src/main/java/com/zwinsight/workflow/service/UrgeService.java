package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.UrgeNotifyEvent;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfUrgeConfig;
import com.zwinsight.workflow.domain.WfUrgeRecord;
import com.zwinsight.workflow.mapper.WfUrgeConfigMapper;
import com.zwinsight.workflow.mapper.WfUrgeRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 催办服务 - 支持自动催办（定时扫描超时待办）和手动催办
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrgeService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final WfUrgeConfigMapper urgeConfigMapper;
    private final WfUrgeRecordMapper urgeRecordMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** 默认超时时间（小时） */
    private static final int DEFAULT_TIMEOUT_HOURS = 24;
    /** 默认催办间隔（小时） */
    private static final int DEFAULT_INTERVAL_HOURS = 4;
    /** 默认最大催办次数 */
    private static final int DEFAULT_MAX_URGE_COUNT = 3;

    /**
     * 自动催办 - 被定时任务调用
     * 扫描所有超时未处理的待办任务，按配置进行催办
     *
     * @return 本次催办的任务数
     */
    @Transactional(rollbackFor = Exception.class)
    public int autoUrge() {
        WfUrgeConfig config = getEffectiveConfig();

        // 自动催办未启用
        if (config.getAutoUrgeEnabled() == null || config.getAutoUrgeEnabled() != 1) {
            log.debug("自动催办未启用，跳过扫描");
            return 0;
        }

        int timeoutHours = config.getTimeoutHours() != null ? config.getTimeoutHours() : DEFAULT_TIMEOUT_HOURS;
        int intervalHours = config.getIntervalHours() != null ? config.getIntervalHours() : DEFAULT_INTERVAL_HOURS;
        int maxUrgeCount = config.getMaxUrgeCount() != null ? config.getMaxUrgeCount() : DEFAULT_MAX_URGE_COUNT;

        // 计算超时阈值时间
        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(timeoutHours);
        Date thresholdDate = Date.from(thresholdTime.atZone(ZoneId.systemDefault()).toInstant());

        // 查询所有创建时间早于阈值的待办任务
        List<Task> overdueTasks = taskService.createTaskQuery()
                .taskCreatedBefore(thresholdDate)
                .list();

        int urgedCount = 0;
        for (Task task : overdueTasks) {
            if (task.getAssignee() == null) {
                continue;
            }

            // 检查催办次数是否已达上限
            int currentCount = urgeRecordMapper.countByTaskId(task.getId());
            if (currentCount >= maxUrgeCount) {
                log.debug("任务 {} 催办次数已达上限 {}/{}", task.getId(), currentCount, maxUrgeCount);
                continue;
            }

            // 检查距上次催办是否已超过间隔时间
            if (!isIntervalExceeded(task.getId(), intervalHours)) {
                log.debug("任务 {} 距上次催办未超过间隔 {}h，跳过", task.getId(), intervalHours);
                continue;
            }

            // 执行催办
            doUrge(task, "SYSTEM", "AUTO", buildAutoUrgeMessage(task));
            urgedCount++;
        }

        return urgedCount;
    }

    /**
     * 手动催办 - 发起人催办当前处理人
     *
     * @param taskId 任务ID
     * @param urgeBy 催办人用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualUrge(String taskId, String urgeBy) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException("任务不存在或已被处理: " + taskId);
        }

        if (task.getAssignee() == null) {
            throw new BusinessException("该任务暂无处理人，无法催办");
        }

        // 验证催办人是否为流程发起人
        Map<String, Object> variables = runtimeService.getVariables(task.getProcessInstanceId());
        String initiator = (String) variables.get("initiator");
        if (initiator == null || !initiator.equals(urgeBy)) {
            throw new BusinessException("仅流程发起人可以催办");
        }

        WfUrgeConfig config = getEffectiveConfig();
        int intervalHours = config.getIntervalHours() != null ? config.getIntervalHours() : DEFAULT_INTERVAL_HOURS;
        int maxUrgeCount = config.getMaxUrgeCount() != null ? config.getMaxUrgeCount() : DEFAULT_MAX_URGE_COUNT;

        // 检查催办次数限制（手动催办同样受限）
        int currentCount = countManualUrge(taskId);
        if (currentCount >= maxUrgeCount) {
            throw new BusinessException("催办次数已达上限（" + maxUrgeCount + "次），请耐心等待处理");
        }

        // 检查催办间隔
        if (!isIntervalExceeded(taskId, intervalHours)) {
            throw new BusinessException("催办间隔不足，请稍后再试");
        }

        // 执行催办
        String message = "您有一条待办任务【" + task.getName() + "】需要尽快处理，发起人正在催办。";
        doUrge(task, urgeBy, "MANUAL", message);

        log.info("手动催办成功, taskId={}, urgeBy={}, assignee={}", taskId, urgeBy, task.getAssignee());
    }

    /**
     * 查询指定任务的催办记录数
     */
    public int getUrgeCount(String taskId) {
        return urgeRecordMapper.countByTaskId(taskId);
    }

    // ===== 私有方法 =====

    /**
     * 执行催办动作：记录 + 发送通知事件
     */
    private void doUrge(Task task, String urgeBy, String urgeType, String message) {
        // 保存催办记录
        WfUrgeRecord record = new WfUrgeRecord();
        record.setProcessInstanceId(task.getProcessInstanceId());
        record.setTaskId(task.getId());
        record.setTaskName(task.getName());
        record.setAssignee(task.getAssignee());
        record.setUrgeBy(urgeBy);
        record.setUrgeType(urgeType);
        record.setUrgeMessage(message);
        record.setUrgeTime(LocalDateTime.now());
        urgeRecordMapper.insert(record);

        // 发布催办通知事件（由 message 模块监听处理）
        Long targetUserId = Long.parseLong(task.getAssignee());
        String title = "【催办通知】" + task.getName();
        UrgeNotifyEvent event = new UrgeNotifyEvent(
                this, targetUserId, title, message,
                task.getProcessInstanceId(), task.getId()
        );
        eventPublisher.publishEvent(event);

        log.info("催办通知已发送, taskId={}, assignee={}, type={}", task.getId(), task.getAssignee(), urgeType);
    }

    /**
     * 获取生效的催办配置（取第一条有效配置，若不存在则使用默认值）
     */
    private WfUrgeConfig getEffectiveConfig() {
        LambdaQueryWrapper<WfUrgeConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(WfUrgeConfig::getCreatedAt)
                .last("LIMIT 1");
        WfUrgeConfig config = urgeConfigMapper.selectOne(wrapper);

        if (config == null) {
            // 返回默认配置
            config = new WfUrgeConfig();
            config.setTimeoutHours(DEFAULT_TIMEOUT_HOURS);
            config.setIntervalHours(DEFAULT_INTERVAL_HOURS);
            config.setMaxUrgeCount(DEFAULT_MAX_URGE_COUNT);
            config.setAutoUrgeEnabled(1);
        }
        return config;
    }

    /**
     * 检查距上次催办是否已超过间隔时间
     */
    private boolean isIntervalExceeded(String taskId, int intervalHours) {
        LambdaQueryWrapper<WfUrgeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfUrgeRecord::getTaskId, taskId)
                .orderByDesc(WfUrgeRecord::getUrgeTime)
                .last("LIMIT 1");
        WfUrgeRecord lastRecord = urgeRecordMapper.selectOne(wrapper);

        if (lastRecord == null) {
            return true; // 从未催办过
        }

        LocalDateTime nextAllowedTime = lastRecord.getUrgeTime().plusHours(intervalHours);
        return LocalDateTime.now().isAfter(nextAllowedTime);
    }

    /**
     * 统计手动催办次数
     */
    private int countManualUrge(String taskId) {
        LambdaQueryWrapper<WfUrgeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfUrgeRecord::getTaskId, taskId)
                .eq(WfUrgeRecord::getUrgeType, "MANUAL");
        return Math.toIntExact(urgeRecordMapper.selectCount(wrapper));
    }

    /**
     * 构建自动催办消息
     */
    private String buildAutoUrgeMessage(Task task) {
        return "您有一条待办任务【" + task.getName() + "】已超时未处理，请尽快办理。";
    }
}
