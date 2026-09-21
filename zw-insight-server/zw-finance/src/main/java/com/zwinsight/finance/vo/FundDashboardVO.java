package com.zwinsight.finance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 老板资金看板 VO（核心三大指标 + 辅助指标）
 * <p>设计立场：系统做给老板看——垫资回答"钱压了多少"、现金流回答"主业造血行不行"、
 * 回款率回答"钱收回来没"（docs/资金流转深度调研报告.md 第七节）。</p>
 */
@Data
public class FundDashboardVO {

    /** 项目ID（NULL=公司整体聚合） */
    private Long projectId;

    // ==================== 核心一：垫资分析 ====================

    /** 累计产值 */
    private BigDecimal cumulativeOutput;

    /** 累计收款（总收入） */
    private BigDecimal cumulativeReceived;

    /** 垫资总额 = 累计产值 - 累计收款 */
    private BigDecimal advanceAmount;

    /** 垫资率 = 垫资 / 累计产值（产值=0时为0） */
    private BigDecimal advanceRate;

    // ==================== 核心二：经营性现金流 ====================

    /** 经营性现金流入（累计收款） */
    private BigDecimal cashInflow;

    /** 经营性现金流出（累计付款，付款口径与 total_expense 一致） */
    private BigDecimal cashOutflow;

    /** 经营性现金流净额 = 流入 - 流出 */
    private BigDecimal operatingCashFlow;

    // ==================== 核心三：回款分析 ====================

    /** 应收账款余额（已结算未收口径） */
    private BigDecimal receivableTotal;

    /** 回款率 = 累计收款 / (累计收款 + 应收) （分母为0时为0） */
    private BigDecimal collectionRate;

    // ==================== 辅助：保证金与专户 ====================

    /** 保证金占用总额（四类，DEPOSITED+USED） */
    private BigDecimal bondOccupying;

    /** 保函替代率 */
    private BigDecimal guaranteeRatio;

    /** 工资专户合规预警数（OVERDUE/INSUFFICIENT） */
    private Integer wageAccountWarnings;

    // ==================== 滚动预测 ====================

    /** 未来月份缺口列表（来自滚动预测快照） */
    private List<GapItem> rollingGaps;

    /** 单月缺口条目 */
    @Data
    public static class GapItem {
        /** 预测月份（YYYY-MM） */
        private String month;
        /** 预计收款 */
        private BigDecimal expectedReceipts;
        /** 预计付款 */
        private BigDecimal expectedPayments;
        /** 净缺口 */
        private BigDecimal netGap;
        /** 风险等级 */
        private String riskLevel;
    }
}
