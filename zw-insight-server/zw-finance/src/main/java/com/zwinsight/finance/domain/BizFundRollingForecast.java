package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资金滚动预测快照实体（资金计划三层之三：滚动预测）
 * <p>预计收款 = 应收台账 OPEN 余额按到期日落月（无台账时回退月度计划 income_plan）；
 * 预计付款 = 已审批未付（pay_status≠PAID）按付款日期落月，<b>并将已逾期未付计入当月</b>；
 * 净缺口 = 付款 - 收款。</p>
 * <p><b>为何逾期要计入当月</b>（V2026_63）：已过计划付款日但仍未支付的单据
 * 随时可能形成现金流出，是最紧迫的资金压力；若按原口径只统计未来窗口，
 * 这部分待付款会在预测中完全消失（线上实测 5850 万逾期款被漏计，
 * 风险等级本应 HIGH 却显示 LOW）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_fund_rolling_forecast")
public class BizFundRollingForecast extends BaseEntity {

    /** 项目ID（NULL为公司整体） */
    private Long projectId;

    /** 预测月份（YYYY-MM） */
    private String forecastMonth;

    /** 预计收款（应收台账 OPEN 余额按到期日落月） */
    private BigDecimal expectedReceipts;

    /** 预计付款（当月计划内未付 + 已逾期未付，即当月预计现金流出总额） */
    private BigDecimal expectedPayments;

    /**
     * 其中：已逾期未付金额（V2026_63）
     * <p>payment_date 早于当月月初且 pay_status≠PAID 的已审批单据合计，
     * 已包含在 {@link #expectedPayments} 内（构成项而非另计项，不可相加）；
     * 仅当月快照非 0，后续月份为 0。单列本字段是为了前端能如实展示
     * “多少是计划内、多少是逾期堆积”，不隐藏构成。</p>
     */
    private BigDecimal overdueUnpaid;

    /** 净缺口（付款-收款，负数为盈余） */
    private BigDecimal netGap;

    /** 风险等级（LOW/MEDIUM/HIGH） */
    private String riskLevel;

    /** 快照生成日期 */
    private LocalDate snapshotDate;
}
