package com.zwinsight.project.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 项目实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_project")
public class BizProject extends BaseEntity {

    /**
     * 项目编号
     */
    private String projectCode;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目性质
     */
    private String projectNature;

    /**
     * 项目类型
     */
    private String projectType;

    /**
     * 业主单位ID
     */
    private Long ownerCompanyId;

    /**
     * 业主单位名称
     */
    private String ownerCompanyName;

    /**
     * 签约公司ID
     */
    private Long signingCompanyId;

    /**
     * 签约公司名称
     */
    private String signingCompanyName;

    /**
     * 项目概述
     */
    private String projectOverview;

    /**
     * 项目地址
     */
    private String projectAddress;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 是否需要招标（1-是 0-否）
     */
    private Integer needTender;

    /** 计划开工日期（V2026_81 周期字段链，蓝图 A2） */
    private LocalDate plannedStartDate;

    /** 计划竣工日期 */
    private LocalDate plannedEndDate;

    /** 实际开工日期（START_CONSTRUCTION 事件首次回写） */
    private LocalDate actualStartDate;

    /** 实际竣工日期（COMPLETE 事件回写） */
    private LocalDate actualEndDate;

    /** 暂停原因（PAUSED 态留痕，V2026_81 A4） */
    private String pauseReason;

    /** 终止原因（TERMINATED 态，走审批，V2026_81 A4） */
    private String terminateReason;

    /** 落标原因（LOST 态，V2026_81 A3） */
    private String lostReason;

    /**
     * 项目状态（DRAFT/FILED/TENDERING/WON/LOST/CONSTRUCTION/PAUSED/COMPLETED/CLOSING/CLOSED/TERMINATED）
     */
    private String status;

    /**
     * 结项审批流程实例ID
     */
    private String workflowInstanceId;

    /**
     * 预算金额
     */
    private BigDecimal budgetAmount;

    /**
     * 合同金额
     */
    private BigDecimal contractAmount;

    /**
     * 累计产值
     */
    private BigDecimal cumulativeOutput;

    /**
     * 应收账款（已结算未收口径，结算审批通过时回写；53_V2026_51 新增）
     */
    private BigDecimal receivableAmount;

    /**
     * 结算金额
     */
    private BigDecimal settlementAmount;

    /**
     * 总收入
     */
    private BigDecimal totalIncome;

    /**
     * 总支出
     */
    private BigDecimal totalExpense;

    /**
     * 其他总支付
     */
    private BigDecimal totalOtherPayment;
}
