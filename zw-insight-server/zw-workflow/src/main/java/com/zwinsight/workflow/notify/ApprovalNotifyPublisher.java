package com.zwinsight.workflow.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.ApprovalNotifyEvent;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.domain.WfBusinessType;
import com.zwinsight.workflow.mapper.WfBusinessTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批通知发布器 - 把审批生命周期事件转换为 {@link ApprovalNotifyEvent} 交给 message 模块落站内信。
 * <p>
 * 三类触达：
 * <ul>
 *   <li>{@link #notifyActiveTasks}：新待办产生 → 通知处理人 + 候选人/候选角色成员</li>
 *   <li>{@link #notifyResult}（APPROVED）：流程审批通过 → 通知发起人</li>
 *   <li>{@link #notifyResult}（REJECTED）：驳回/撤回/终止 → 通知发起人</li>
 * </ul>
 * </p>
 * <p><b>为什么由 ApprovalService 显式调用而非监听 Flowable TASK_CREATED</b>：引擎先派发
 * {@code TASK_CREATED}，随后才写入处理人与候选身份链接，事件回调内解析会拿到空收件人
 * （2026-10-13 线上实测：待办通知未落库，而驳回通知正常）。改为审批动作完成后查询该流程实例的
 * <b>活动任务</b>，此时 assignee 与身份链接均已在当前事务内可见。</p>
 * <p><b>时序约束</b>：收件人解析（Flowable/MyBatis 读操作）在业务事务<b>内</b>同步完成；仅「发布事件」
 * 延迟到事务提交后（{@link #publishAfterCommit}）。这样 ① 主事务回滚时不留「幽灵消息」；
 * ② 提交后阶段不再执行任何数据库访问（该阶段事务资源仍绑定但已不会再提交，不宜做数据访问）。
 * 通知失败一律吞并记日志，不得影响审批主流程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalNotifyPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final SysUserMapper sysUserMapper;
    private final WfBusinessTypeMapper businessTypeMapper;

    /** 流程引擎服务：延迟注入避免与 FlowableConfig 的监听器注册形成构造期循环依赖 */
    @Lazy
    @Autowired
    private TaskService taskService;

    /**
     * 新待办通知：通知流程实例当前全部活动任务的处理人/候选人。
     * <p>由 {@code ApprovalService} 在发起流程/办理/退回/转办/委托后调用。流程已结束
     * （无活动任务）时静默返回，由审批结果通知负责触达。</p>
     *
     * @param processInstanceId 流程实例ID
     */
    public void notifyActiveTasks(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        try {
            List<Task> activeTasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
            // info 级别：上次线上待办通知未落库却无任何日志，跳过分支必须可观测
            log.info("待办通知：processInstanceId={}, 活动任务数={}", processInstanceId,
                    activeTasks == null ? 0 : activeTasks.size());
            if (activeTasks == null || activeTasks.isEmpty()) {
                return;
            }
            for (Task task : activeTasks) {
                notifyTask(task);
            }
        } catch (Exception e) {
            log.error("待办通知失败（不影响审批流程）, processInstanceId={}", processInstanceId, e);
        }
    }

    /**
     * 审批结果通知（通知流程发起人）。
     *
     * @param businessType      业务类型编码
     * @param businessId        业务单据ID
     * @param processInstanceId 流程实例ID
     * @param initiator         发起人用户ID字符串（来自流程变量/历史实例，可能为空）
     * @param result            APPROVED=通过；其余（REJECT/WITHDRAW）=驳回/撤回
     * @param tenantId          租户ID（异步监听器恢复上下文用）
     */
    public void notifyResult(String businessType, Long businessId, String processInstanceId,
                             String initiator, String result, Long tenantId) {
        try {
            Long initiatorId = parseLong(initiator);
            if (initiatorId == null || tenantId == null) {
                return;
            }
            boolean approved = ApprovalNotifyEvent.TYPE_APPROVED.equals(result);
            String bizName = resolveBusinessName(businessType, tenantId);
            String title = approved ? "【审批通过】" + bizName : "【审批驳回】" + bizName;
            String content = approved
                    ? "您提交的【" + bizName + "】已审批通过。"
                    : "您提交的【" + bizName + "】已被驳回、退回或撤回，请在审批记录中查看原因。";
            publishAfterCommit(new ApprovalNotifyEvent(this, Collections.singletonList(initiatorId),
                    approved ? ApprovalNotifyEvent.TYPE_APPROVED : ApprovalNotifyEvent.TYPE_REJECTED,
                    title, content, businessType, businessId, processInstanceId, null, tenantId));
        } catch (Exception e) {
            log.error("审批结果通知失败（不影响审批流程）, businessType={}, businessId={}, result={}",
                    businessType, businessId, result, e);
        }
    }

    // ===== 内部实现 =====

    /** 解析单个活动任务的收件人并（提交后）发布待办通知 */
    private void notifyTask(Task task) {
        Long tenantId = parseLong(task.getTenantId());
        if (tenantId == null) {
            log.warn("待办通知跳过：任务缺少租户, taskId={}", task.getId());
            return;
        }
        String taskId = task.getId();
        Map<String, Object> variables = taskService.getVariables(taskId);
        String businessType = variables.get("businessType") == null ? null : String.valueOf(variables.get("businessType"));
        Long businessId = parseLong(variables.get("businessId"));

        Set<Long> recipients = new LinkedHashSet<>();
        // 1. 已指派处理人
        Long assigneeId = parseLong(task.getAssignee());
        if (assigneeId != null) {
            recipients.add(assigneeId);
        }
        // 2. 候选人（按用户ID）与候选组（角色编码 → 租户内用户）
        List<String> candidateGroups = new ArrayList<>();
        for (IdentityLink link : taskService.getIdentityLinksForTask(taskId)) {
            if (!"candidate".equals(link.getType())) {
                continue;
            }
            Long candidateUserId = parseLong(link.getUserId());
            if (candidateUserId != null) {
                recipients.add(candidateUserId);
            }
            if (link.getGroupId() != null && !link.getGroupId().isBlank()) {
                candidateGroups.add(link.getGroupId());
            }
        }
        if (!candidateGroups.isEmpty()) {
            List<Long> groupUsers = sysUserMapper.selectUserIdsByRoleCodes(candidateGroups, tenantId);
            if (groupUsers != null) {
                recipients.addAll(groupUsers);
            }
        }

        if (recipients.isEmpty()) {
            // 候选组尚未配置成员等场景：无收件人即不通知（不伪造发送成功）
            log.info("待办通知跳过：无有效收件人, taskId={}, businessType={}, candidateGroups={}",
                    taskId, businessType, candidateGroups);
            return;
        }

        String bizName = resolveBusinessName(businessType, tenantId);
        String taskName = task.getName() == null ? "待办任务" : task.getName();
        String title = "【待办提醒】" + bizName;
        String content = "您有一条【" + bizName + "】待办任务（" + taskName + "），请及时办理。";
        publishAfterCommit(new ApprovalNotifyEvent(this, new ArrayList<>(recipients),
                ApprovalNotifyEvent.TYPE_TODO, title, content, businessType, businessId,
                task.getProcessInstanceId(), taskId, tenantId));
    }

    /** 业务类型编码 → 中文名；查不到时回退编码本身（不阻断通知） */
    private String resolveBusinessName(String businessType, Long tenantId) {
        if (businessType == null || businessType.isBlank()) {
            return "审批单据";
        }
        try {
            WfBusinessType type = businessTypeMapper.selectOne(new LambdaQueryWrapper<WfBusinessType>()
                    .eq(WfBusinessType::getTypeCode, businessType)
                    .eq(WfBusinessType::getTenantId, tenantId)
                    .last("LIMIT 1"));
            if (type != null && type.getTypeName() != null && !type.getTypeName().isBlank()) {
                return type.getTypeName();
            }
        } catch (Exception e) {
            log.debug("业务类型名称解析失败，回退编码: businessType={}", businessType, e);
        }
        return businessType;
    }

    /**
     * 事务提交后发布事件；无事务上下文时立即发布。
     * <p>提交后阶段只做事件发布（无数据库访问）；主事务回滚则回调不会触发，不留幽灵消息。</p>
     */
    private void publishAfterCommit(ApprovalNotifyEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doPublish(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doPublish(event);
            }
        });
    }

    private void doPublish(ApprovalNotifyEvent event) {
        try {
            eventPublisher.publishEvent(event);
            log.info("审批通知已发布, type={}, businessType={}, businessId={}, 收件人数={}",
                    event.getNotifyType(), event.getBusinessType(), event.getBusinessId(),
                    event.getTargetUserIds().size());
        } catch (Exception e) {
            log.error("审批通知发布失败, type={}, businessType={}, businessId={}",
                    event.getNotifyType(), event.getBusinessType(), event.getBusinessId(), e);
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
