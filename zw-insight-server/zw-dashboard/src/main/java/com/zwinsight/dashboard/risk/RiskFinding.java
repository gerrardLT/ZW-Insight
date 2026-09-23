package com.zwinsight.dashboard.risk;

import java.math.BigDecimal;

/**
 * 风险扫描结果条目（RiskRule 产出，RiskScanService 负责 upsert 入台账）
 *
 * @param riskType     风险类型（PROFIT_LOSS/BUDGET_OVER/FUND_GAP/RECEIVABLE_OVERDUE/RETENTION_OVERDUE/WAGE_COMPLIANCE）
 * @param projectId    项目ID（公司级风险可空）
 * @param severity     严重级别（RED/YELLOW/INFO，规则自动判定）
 * @param title        发生了什么（一句话）
 * @param impactAmount 影响金额（元，正数）
 * @param reasonDetail 为什么发生（结构化归因 JSON 字符串，可空）
 * @param nextAction   下一步动作建议
 * @param bizRefType   关联单据类型（穿透跳转用）
 * @param bizRefId     关联单据ID（可空，空则按 projectId 穿透到项目）
 * @param ruleParams   触发规则参数快照 JSON（阈值/执行率等判定依据）
 */
public record RiskFinding(
        String riskType,
        Long projectId,
        String severity,
        String title,
        BigDecimal impactAmount,
        String reasonDetail,
        String nextAction,
        String bizRefType,
        Long bizRefId,
        String ruleParams) {

    /** 风险唯一键（台账幂等去重：类型 + 项目 + 关联单据） */
    public String riskCode() {
        return riskType + ":" + (projectId != null ? projectId : "COMPANY")
                + ":" + (bizRefId != null ? bizRefId : "ALL");
    }
}
