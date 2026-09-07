package com.zwinsight.contract.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 变更事件业务状态机。
 * <p>
 * <b>业务状态机 ≠ BPMN 审批流</b>：本枚举只描述变更事件自身的生命周期
 * （发生了什么、评估到什么程度、最终结论是什么），而「由谁审、几级审、
 * 会不会退回」交给 Flowable 流程定义。二者通过 workflow_instance_id 关联，
 * 审批流不得反向成为业务模型本身。
 * </p>
 *
 * <pre>
 *   DRAFT ──submitAssess──▶ ASSESSING ──completeAssess──▶ APPROVING ──approve──▶ APPROVED
 *     │                        │                             │
 *     │                        └────── withdraw ─────────────┤
 *     │                                                      ├──reject──▶ REJECTED
 *     └── cancel ──▶ CANCELLED                               └── reAssess ──▶ ASSESSING
 * </pre>
 *
 * <p>非法流转一律抛 {@link IllegalStateException}，由服务层转成业务异常，
 * 避免出现「已批准的变更被改回草稿」这类静默数据损坏。</p>
 */
public enum ChangeEventStatus {

    /** 草稿：现场登记完成，尚未做影响评估 */
    DRAFT,

    /** 评估中：商务/造价正在测算成本与工期影响 */
    ASSESSING,

    /** 审批中：影响评估已完成，进入审批流 */
    APPROVING,

    /** 已批准：终态。触发下游预算/合同/收入变更传导 */
    APPROVED,

    /** 已驳回：终态。可复制为新事件重新发起，但不允许原地改回复用 */
    REJECTED,

    /** 已取消：终态。事件作废（如重复登记、误报） */
    CANCELLED;

    /** 合法流转表：from → 允许的 to 集合 */
    private static final Map<ChangeEventStatus, Set<ChangeEventStatus>> TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(ASSESSING, CANCELLED),
            ASSESSING, EnumSet.of(APPROVING, DRAFT, CANCELLED),
            APPROVING, EnumSet.of(APPROVED, REJECTED, ASSESSING, CANCELLED),
            APPROVED, EnumSet.noneOf(ChangeEventStatus.class),
            REJECTED, EnumSet.noneOf(ChangeEventStatus.class),
            CANCELLED, EnumSet.noneOf(ChangeEventStatus.class)
    );

    /**
     * 校验状态流转是否合法。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return true 表示允许流转
     */
    public static boolean canTransition(ChangeEventStatus from, ChangeEventStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(ChangeEventStatus.class)).contains(to);
    }

    /**
     * 断言流转合法，非法时抛异常（带上下文，便于定位是哪条数据被误操作）。
     *
     * @param from        当前状态
     * @param to          目标状态
     * @param eventNumber 事件编号（错误信息用）
     */
    public static void assertTransition(ChangeEventStatus from, ChangeEventStatus to, String eventNumber) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(String.format(
                    "变更事件[%s]不允许从 %s 流转到 %s",
                    eventNumber,
                    from != null ? from.name() : "null",
                    to != null ? to.name() : "null"));
        }
    }

    /** 是否终态（终态不可再流转，也不可编辑） */
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }

    /** 是否可编辑（仅草稿与评估中允许改内容） */
    public boolean isEditable() {
        return this == DRAFT || this == ASSESSING;
    }

    /**
     * 从字符串安全解析，未知值返回 null 而非抛异常
     * （历史数据可能存在脏状态，读取侧要宽容，写入侧才严格）。
     */
    public static ChangeEventStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ChangeEventStatus s : values()) {
            if (s.name().equalsIgnoreCase(code.trim())) {
                return s;
            }
        }
        return null;
    }

    /** 中文展示名（前端状态标签统一取此处，避免多端各写一套映射） */
    public String getDisplayName() {
        return switch (this) {
            case DRAFT -> "草稿";
            case ASSESSING -> "评估中";
            case APPROVING -> "审批中";
            case APPROVED -> "已批准";
            case REJECTED -> "已驳回";
            case CANCELLED -> "已取消";
        };
    }
}
