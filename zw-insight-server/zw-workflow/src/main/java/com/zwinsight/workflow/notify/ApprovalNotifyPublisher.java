package com.zwinsight.workflow.notify;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.event.ApprovalNotifyEvent;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.workflow.domain.WfBusinessType;
import com.zwinsight.workflow.mapper.WfBusinessTypeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.identitylink.api.IdentityLink;
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
 *   <li>{@link #notifyTodo}：新待办产生 → 通知处理人 + 候选人/候选角色成员</li>
 *   <li>{@link #notifyResult}（APPROVED）：流程审批通过 → 通知发起人</li>
 *   <li>{@link #notifyResult}（REJECTED）：驳回/撤回/终止 → 通知发起人</li>
 * </ul>
 * </p>
 * <p><b>为什么待办通知要等事务提交后再解析收件人</b>：Flowable 在创建用户任务时先派发
 * {@code TASK_CREATED}，随后才写入候选身份链接（candidate user/group）；若在事件回调内立即
 * 查询，候选组任务会解析出空收件人而静默丢通知。这里统一注册 {@code afterCommit} 回调，
 * 事务提交后再查任务与身份链接（此时已落库），且主事务回滚时不发通知（避免幽灵消息）。</p>
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
     * 新待办产生通知（在事务提交后解析收件人并发布）。
     *
     * @param taskId 新创建的待办任务ID
     */
    public void notifyTodo(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        runAfterCommit(() -> doNotifyTodo(taskId));
    }

    /**
     * 审批结果通知（通知流程发起人）。
     * <p>与 {@link #notifyTodo} 一致，统一延迟到事务提交后发布：避免主事务回滚后
     * 留下「幽灵消息」。发布方在提交后调用 {@link #publish}，消费侧为普通 {@code @Async}
     * 监听器（不能再用 {@code @TransactionalEventListener}——提交后阶段已无法再登记
     * 同步回调，事件会被静默丢弃）。</p>
     *
     * @param businessType      业务类型编码
     * @param businessId        业务单据ID
     * @param processInstanceId 流程实例ID
     * @param initiator         发起人用户ID字符串（来自流程变量，可能为空）
     * @param result            APPROVED=通过；其余（REJECT/WITHDRAW）=驳回/撤回
     * @param tenantId          租户ID（异步监听器恢复上下文用）
     */
    public void notifyResult(String businessType, Long businessId, String processInstanceId,
                             String initiator, String result, Long tenantId) {
        runAfterCommit(() -> doNotifyResult(businessType, businessId, processInstanceId,
                initiator, result, tenantId));
    }

    private void doNotifyResult(String businessType, Long businessId, String processInstanceId,
                                String initiator, String result, Long tenantId) {
        try {
            if (initiator == null || initiator.isBlank() || tenantId == null) {
                return;
            }
            Long initiatorId = parseLong(initiator);
            if (initiatorId == null) {
                return;
            }
            boolean approved = ApprovalNotifyEvent.TYPE_APPROVED.equals(result);
            String bizName = resolveBusinessName(businessType, tenantId);
            String title = approved ? "【审批通过】" + bizName : "【审批驳回】" + bizName;
            String content = approved
                    ? "您提交的【" + bizName + "】已审批通过。"
                    : "您提交的【" + bizName + "】已被驳回或撤回，请查看审批意见后修改重提。";
            publish(Collections.singletonList(initiatorId),
                    approved ? ApprovalNotifyEvent.TYPE_APPROVED : ApprovalNotifyEvent.TYPE_REJECTED,
                    title, content, businessType, businessId, processInstanceId, null, tenantId);
        } catch (Exception e) {
            // 通知失败不得影响审批主流程
            log.error("审批结果通知发布失败, businessType={}, businessId={}, result={}",
                    businessType, businessId, result, e);
        }
    }

    // ===== 内部实现 =====

    private void doNotifyTodo(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            // 任务已在同一事务内被推进/删除（如自动完成节点），无需通知
            return;
        }
        Long tenantId = parseLong(task.getTenantId());
        if (tenantId == null) {
            log.warn("待办通知跳过：任务缺少租户, taskId={}", taskId);
            return;
        }

        // 提交后回调可能脱离原租户上下文（异步/线程复用），显式设置以保证 wf_business_type
        // 查询被租户拦截器正确过滤；结束后还原，避免污染线程池复用。
        Long previousTenantId = SecurityContextHolder.getTenantId();
        SecurityContextHolder.setTenantId(tenantId);
        try {
            doNotifyTodoInTenant(task, tenantId);
        } finally {
            SecurityContextHolder.setTenantId(previousTenantId);
        }
    }

    private void doNotifyTodoInTenant(Task task, Long tenantId) {
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
            log.debug("待办通知跳过：无有效收件人, taskId={}, businessType={}", taskId, businessType);
            return;
        }

        String bizName = resolveBusinessName(businessType, tenantId);
        String taskName = task.getName() == null ? "待办任务" : task.getName();
        String title = "【待办提醒】" + bizName;
        String content = "您有一条【" + bizName + "】待办任务（" + taskName + "），请及时办理。";
        publish(new ArrayList<>(recipients), ApprovalNotifyEvent.TYPE_TODO,
                title, content, businessType, businessId, task.getProcessInstanceId(), taskId, tenantId);
    }

    private void publish(List<Long> recipients, String notifyType, String title, String content,
                         String businessType, Long businessId, String processInstanceId,
                         String taskId, Long tenantId) {
        eventPublisher.publishEvent(new ApprovalNotifyEvent(this, recipients, notifyType, title, content,
                businessType, businessId, processInstanceId, taskId, tenantId));
        log.info("审批通知已发布, type={}, businessType={}, businessId={}, 收件人数={}",
                notifyType, businessType, businessId, recipients.size());
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
     * 事务提交后执行；无事务时立即执行。
     * <p>放在提交后是为了让候选身份链接已落库，同时主事务回滚时不发通知。</p>
     */
    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (Exception e) {
                    log.error("审批通知（提交后）执行失败", e);
                }
            }
        });
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
