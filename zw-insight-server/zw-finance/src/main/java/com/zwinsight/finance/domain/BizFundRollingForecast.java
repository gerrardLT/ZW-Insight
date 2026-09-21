package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资金滚动预测快照实体（资金计划三层之三：滚动预测）
 * <p>预计收款 = 已结算未收；预计付款 = 已审批未付；净缺口 = 付款 - 收款。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_fund_rolling_forecast")
public class BizFundRollingForecast extends BaseEntity {

    /** 项目ID（NULL为公司整体） */
    private Long projectId;

    /** 预测月份（YYYY-MM） */
    private String forecastMonth;

    /** 预计收款（已结算未收 + 预计确权） */
    private BigDecimal expectedReceipts;

    /** 预计付款（已审批未付 + 必付项） */
    private BigDecimal expectedPayments;

    /** 净缺口（付款-收款，负数为盈余） */
    private BigDecimal netGap;

    /** 风险等级（LOW/MEDIUM/HIGH） */
    private String riskLevel;

    /** 快照生成日期 */
    private LocalDate snapshotDate;
}
