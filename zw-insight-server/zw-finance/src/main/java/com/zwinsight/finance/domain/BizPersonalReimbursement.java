package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 个人报销实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_personal_reimbursement")
public class BizPersonalReimbursement extends BaseEntity {

    /** 报销总金额 */
    private BigDecimal totalAmount;

    /** 报销日期（业务日期，用于封账校验） */
    private LocalDate reimbursementDate;

    /** 备注 */
    private String remark;

    /** 状态（DRAFT/APPROVED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;

    /**
     * 费用科目明细（请求/展示透传，不持久化；落库于 biz_reimbursement_detail，V2026_60）。
     * <p>本表无 projectId 列，若明细为项目相关招待费，
     * 需由 {@link BizEntertainmentDetail#getProjectId()} 显式携带项目归属。</p>
     */
    @TableField(exist = false)
    private List<BizReimbursementDetail> details;
}
