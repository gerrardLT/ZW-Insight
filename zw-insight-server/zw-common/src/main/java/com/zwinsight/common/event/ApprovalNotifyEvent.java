package com.zwinsight.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 审批通知事件 - 由 workflow 模块发布，message 模块监听落站内信（+WebSocket 实时推送）。
 * <p>
 * 覆盖审批生命周期的三类触达：
 * <ul>
 *   <li>{@link #TYPE_TODO}：新待办产生（流程推进到某审批节点）→ 通知处理人/候选人</li>
 *   <li>{@link #TYPE_APPROVED}：流程审批通过 → 通知发起人</li>
 *   <li>{@link #TYPE_REJECTED}：流程被驳回/撤回/终止 → 通知发起人</li>
 * </ul>
 * </p>
 * <p><b>租户上下文</b>：监听器为 {@code @Async}，ThreadLocal 租户上下文不随异步线程继承，
 * 而 {@code msg_message} 插入含 tenantId 字段；发布方必须显式传入 {@code tenantId}，
 * 监听器据此在异步线程内设置上下文（与 {@link UrgeNotifyEvent} 同款约束，
 * 否则 INSERT 被写防护拒绝、站内信静默丢失）。</p>
 */
@Getter
public class ApprovalNotifyEvent extends ApplicationEvent {

    /** 新待办产生 */
    public static final String TYPE_TODO = "TODO";
    /** 审批通过 */
    public static final String TYPE_APPROVED = "APPROVED";
    /** 驳回/撤回/终止 */
    public static final String TYPE_REJECTED = "REJECTED";

    /** 接收人用户ID列表（已由发布方解析为具体用户，含去重） */
    private final List<Long> targetUserIds;

    /** 通知类型：TODO / APPROVED / REJECTED */
    private final String notifyType;

    /** 消息标题 */
    private final String title;

    /** 消息内容 */
    private final String content;

    /** 业务类型编码（对应 workflow business-type，如 PAYMENT_APPLY） */
    private final String businessType;

    /** 业务单据ID */
    private final Long businessId;

    /** 流程实例ID（待办通知可空） */
    private final String processInstanceId;

    /** 任务ID（仅待办通知有值，供前端跳转定位） */
    private final String taskId;

    /** 租户ID（异步监听器恢复上下文用，必填） */
    private final Long tenantId;

    public ApprovalNotifyEvent(Object source, List<Long> targetUserIds, String notifyType,
                               String title, String content, String businessType, Long businessId,
                               String processInstanceId, String taskId, Long tenantId) {
        super(source);
        this.targetUserIds = targetUserIds;
        this.notifyType = notifyType;
        this.title = title;
        this.content = content;
        this.businessType = businessType;
        this.businessId = businessId;
        this.processInstanceId = processInstanceId;
        this.taskId = taskId;
        this.tenantId = tenantId;
    }
}
