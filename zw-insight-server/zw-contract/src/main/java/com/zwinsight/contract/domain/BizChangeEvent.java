package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 变更事件实体（Change Event）—— 跨域变更主链的起点。
 * <p>
 * 对标 Procore Change Events / Oracle Unifier Change Orders 的分层设计：
 * <b>现场事件（发生了什么）</b> 与 <b>合同变更（钱怎么变）</b> 分离。
 * 现场先低成本登记事件，再由商务做影响评估，审批通过后才驱动
 * 预算变更（CBS current_amount）、合同变更（累计变更金额）与收入变更。
 * </p>
 * <p>
 * 关键约束：{@code sourceType + sourceRef} 唯一标识业务事实来源，
 * 同一张签证/巡检单不允许重复登记为多个变更事件（防止业务事实重复录入）。
 * </p>
 * <p>
 * 状态机（业务生命周期）与 BPMN 审批流（谁审批）严格分离：
 * {@code status} 承载业务状态，{@code workflowInstanceId} 仅为流程关联指针。
 * </p>
 *
 * @see ChangeEventStatus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_change_event", autoResultMap = true)
public class BizChangeEvent extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 变更事件编号（编号规则 CHANGE_EVENT，如 CHG20260001） */
    private String eventNumber;

    /** 来源类型（FIELD_EVENT/DESIGN_CHANGE/OWNER_REQUEST/OTHER） */
    private String sourceType;

    /** 来源引用（签证ID/巡检ID 等，用于业务事实去重与全链路追溯） */
    private String sourceRef;

    /** 标题 */
    private String title;

    /** 详细描述 */
    private String description;

    /** 影响类别（COST_IMPACT/SCOPE_CHANGE/SCHEDULE_DELAY/QUALITY_ISSUE） */
    private String category;

    /** 影响的 WBS 节点ID 数组 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> affectedWbsIds;

    /** 影响的成本账户数组（评估阶段填写，审批通过后据此调整 current_amount） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<AffectedAccount> affectedAccounts;

    /** 佐证附件数组 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<SupportingDoc> supportingDocs;

    /** 影响评估（成本/工期/理由），JSON 结构便于扩展而不频繁改表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private ImpactAssessment impactAssessment;

    /** 成本影响金额（冗余自 impactAssessment，供列表排序与统计走索引） */
    private BigDecimal costDelta;

    /** 工期影响天数（冗余自 impactAssessment） */
    private Integer scheduleDelayDays;

    /** 业务状态（DRAFT/ASSESSING/APPROVING/APPROVED/REJECTED/CANCELLED） */
    private String status;

    /** 影响评估人ID */
    private Long assessedBy;

    /** 影响评估时间 */
    private LocalDateTime assessedAt;

    /** 审批人ID */
    private Long approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    /** 驳回原因 */
    private String rejectionReason;

    /** 流程实例ID（仅关联指针，不承载业务状态） */
    private String workflowInstanceId;

    /** 项目名称（冗余展示字段，不持久化） */
    @TableField(exist = false)
    private String projectName;

    /** 创建人姓名（列表展示，不持久化） */
    @TableField(exist = false)
    private String createdByName;

    // ==================== 嵌套值对象 ====================

    /**
     * 影响评估值对象。
     * <p>成本与工期影响是变更决策的两个核心维度，缺一不可（允许为 0）。</p>
     */
    @Data
    public static class ImpactAssessment {
        /** 成本影响金额（正=增加成本，负=节约） */
        private BigDecimal costDelta;
        /** 工期影响天数（正=延误，负=提前） */
        private Integer scheduleDelayDays;
        /** 评估理由（审批依据，必填） */
        private String rationale;
        /** 评估备注 */
        private String assessmentNotes;
    }

    /**
     * 受影响的成本账户及调整额。
     * <p>审批通过后由 ChangeEventApprovalHandler 按此明细调整 CBS current_amount，
     * 保证「变更 → 预算」的传导有据可查、可回溯、可回滚。</p>
     */
    @Data
    public static class AffectedAccount {
        /** 成本账户ID */
        private Long accountId;
        /** 调整方向（INCREASE-增加/DECREASE-减少） */
        private String deltaType;
        /** 调整金额（绝对值，方向由 deltaType 决定） */
        private BigDecimal deltaAmount;
    }

    /** 佐证附件（复用既有 file_info 体系，只存引用不存二进制） */
    @Data
    public static class SupportingDoc {
        /** 文件ID（file_info.id） */
        private Long fileId;
        /** 访问URL */
        private String url;
        /** 文件名 */
        private String name;
        /** 文件类型/ MIME */
        private String type;
    }

    // ==================== 便捷方法 ====================

    /** 取成本影响（优先冗余列，回落到评估对象，永不为 null） */
    public BigDecimal resolveCostDelta() {
        if (costDelta != null) {
            return costDelta;
        }
        if (impactAssessment != null && impactAssessment.getCostDelta() != null) {
            return impactAssessment.getCostDelta();
        }
        return BigDecimal.ZERO;
    }

    /** 取工期影响天数（永不为 null） */
    public int resolveScheduleDelayDays() {
        if (scheduleDelayDays != null) {
            return scheduleDelayDays;
        }
        if (impactAssessment != null && impactAssessment.getScheduleDelayDays() != null) {
            return impactAssessment.getScheduleDelayDays();
        }
        return 0;
    }

    /** 受影响的成本账户明细（永不为 null） */
    public List<AffectedAccount> resolveAffectedAccounts() {
        return affectedAccounts != null ? affectedAccounts : new ArrayList<>();
    }

    /** 是否已终态（不可再流转） */
    public boolean isTerminal() {
        return ChangeEventStatus.APPROVED.name().equals(status)
                || ChangeEventStatus.REJECTED.name().equals(status)
                || ChangeEventStatus.CANCELLED.name().equals(status);
    }
}
