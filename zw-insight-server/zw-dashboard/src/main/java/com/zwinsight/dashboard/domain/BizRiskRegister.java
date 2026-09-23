package com.zwinsight.dashboard.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一风险台账实体（V2026_59，驾驶舱 V1 §11-12 风险中心数据源）
 * <p>
 * 每条风险回答六要素：发生了什么（title）/ 影响多少钱（impactAmount）/ 为什么
 * （reasonDetail JSON）/ 谁负责（ownerId/ownerName）/ 下一步动作（nextAction）/
 * 当前处理状态（handleStatus）。severity 由 {@code RiskRule} 规则自动判定，
 * 禁止人工改级（对齐文档 §15"状态必须由规则自动产生"）。
 * </p>
 * <p>幂等键 riskCode = riskType:projectId:bizRefId，由 RiskScanService upsert；
 * 风险消失时自动 RESOLVED（人工 IGNORED 的不再自动重开，除非规则升级判级）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_risk_register")
public class BizRiskRegister extends BaseEntity {

    /** 严重级别：严重（需管理层介入） */
    public static final String SEVERITY_RED = "RED";
    /** 严重级别：关注（已出现明显偏差） */
    public static final String SEVERITY_YELLOW = "YELLOW";
    /** 严重级别：提醒（普通信息） */
    public static final String SEVERITY_INFO = "INFO";

    /** 处理状态：待处理 */
    public static final String HANDLE_OPEN = "OPEN";
    /** 处理状态：处理中 */
    public static final String HANDLE_PROCESSING = "PROCESSING";
    /** 处理状态：已解决 */
    public static final String HANDLE_RESOLVED = "RESOLVED";
    /** 处理状态：已忽略（人工消音，记录操作人） */
    public static final String HANDLE_IGNORED = "IGNORED";

    /** 风险唯一键（ruleType:projectId:bizRefId） */
    private String riskCode;

    /** 风险类型（PROFIT_LOSS/BUDGET_OVER/FUND_GAP/RECEIVABLE_OVERDUE/RETENTION_OVERDUE/WAGE_COMPLIANCE） */
    private String riskType;

    /** 项目ID（公司级风险可空） */
    private Long projectId;

    /** 项目名称（冗余展示字段，不持久化） */
    @TableField(exist = false)
    private String projectName;

    /** 严重级别（RED/YELLOW/INFO，规则自动判定） */
    private String severity;

    /** 发生了什么（一句话） */
    private String title;

    /** 影响金额（元） */
    private BigDecimal impactAmount;

    /** 为什么发生（结构化归因 JSON 字符串） */
    private String reasonDetail;

    /** 责任人用户ID */
    private Long ownerId;

    /** 责任人姓名（快照冗余） */
    private String ownerName;

    /** 下一步动作建议 */
    private String nextAction;

    /** 处理状态（OPEN/PROCESSING/RESOLVED/IGNORED） */
    private String handleStatus;

    /** 处理人ID */
    private Long handledBy;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 处理备注 */
    private String handleNote;

    /** 关联单据类型（穿透跳转用） */
    private String bizRefType;

    /** 关联单据ID */
    private Long bizRefId;

    /** 触发规则参数快照 JSON（阈值/执行率等判定依据） */
    private String ruleParams;

    /** 最近扫描确认时间 */
    private LocalDateTime lastScanAt;
}
