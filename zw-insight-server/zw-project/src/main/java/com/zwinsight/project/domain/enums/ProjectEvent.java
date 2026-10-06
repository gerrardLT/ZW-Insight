package com.zwinsight.project.domain.enums;

/**
 * 项目状态机事件（P1-M1 深度优化，蓝图 §5.1）。
 * <p>
 * 事件是状态流转的唯一合法触发器：任何状态变更必须经由
 * {@code ProjectStateMachine.fire(project, event, ...)}，禁止散落的 setStatus 直写。
 * 跨模块联动（投标中标/落标、施工合同生效、竣工验收）同样以事件注入，
 * 保证转移边校验与流转日志全局一致。
 * </p>
 */
public enum ProjectEvent {

    /** 提交立项（DRAFT→FILED；受 sys_config 开关控制是否走审批） */
    SUBMIT,
    /** 撤回立项（FILED→DRAFT，业务信息有误收回修改） */
    WITHDRAW,
    /** 进入投标（FILED→TENDERING，needTender=1 时由投标报名触发） */
    GO_TENDER,
    /** 中标（TENDERING→WON，开标记录触发） */
    WIN_BID,
    /** 落标（TENDERING→LOST 终态，开标记录/手工归档触发，须记原因） */
    LOSE_BID,
    /** 开工（WON/FILED→CONSTRUCTION，施工合同生效触发；守卫：存在 EFFECTIVE 施工合同） */
    START_CONSTRUCTION,
    /** 暂停（CONSTRUCTION→PAUSED，留痕原因） */
    PAUSE,
    /** 复工（PAUSED→CONSTRUCTION） */
    RESUME,
    /** 竣工（CONSTRUCTION→COMPLETED，竣工验收单审批通过触发；回写实际竣工日） */
    COMPLETE,
    /** 发起结项（COMPLETED→CLOSING） */
    APPLY_CLOSE,
    /** 结项审批通过（CLOSING→CLOSED 终态） */
    APPROVE_CLOSE,
    /** 结项审批驳回（CLOSING→COMPLETED 回退） */
    REJECT_CLOSE,
    /** 发起终止审批（非终态→TERMINATING 中间态，须记原因） */
    TERMINATE_APPLY,
    /** 终止审批通过（TERMINATING→TERMINATED 终态） */
    TERMINATE_APPROVED,
    /** 终止审批驳回（TERMINATING→回退发起前状态，恢复目标从流转日志回溯） */
    TERMINATE_REJECTED
}
