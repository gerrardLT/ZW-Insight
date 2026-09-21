package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 金额分级审批配置实体（sys_ 前缀：系统级配置表，参照 sys_budget_control_config 先例）
 * <p>档位等级作为 Flowable 流程变量 approvalTier 传入审批流程，
 * 由 BPMN 条件网关路由到对应审批节点（如 &gt;50 万走财务负责人）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_amount_tier_config")
public class SysAmountTierConfig extends BaseEntity {

    /** 业务模块：付款申请 */
    public static final String MODULE_PAYMENT_APPLY = "PAYMENT_APPLY";

    /** 业务模块（PAYMENT_APPLY） */
    private String module;

    /** 审批档位等级（1-普通/2-部门负责人/3-财务负责人/4-老板） */
    private Integer tierLevel;

    /** 档位名称（如 财务负责人审批） */
    private String tierName;

    /** 金额下限（含） */
    private BigDecimal minAmount;

    /** 金额上限（不含；NULL表示无上限） */
    private BigDecimal maxAmount;

    /** 是否启用（1-是 0-否） */
    private Integer enabled;
}
