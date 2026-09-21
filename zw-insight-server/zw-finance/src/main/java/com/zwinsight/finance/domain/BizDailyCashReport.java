package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资金日报快照实体（每日头寸，老板视角）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_daily_cash_report")
public class BizDailyCashReport extends BaseEntity {

    /** 报告日期 */
    private LocalDate reportDate;

    /** 全部账户余额合计（可用头寸） */
    private BigDecimal totalBalance;

    /** 基本户余额 */
    private BigDecimal basicBalance;

    /** 一般户余额 */
    private BigDecimal generalBalance;

    /** 专户户余额 */
    private BigDecimal specialBalance;

    /** 当日资金流入（流水 IN 汇总） */
    private BigDecimal inflowAmount;

    /** 当日资金流出（流水 OUT 汇总） */
    private BigDecimal outflowAmount;

    /** 当日净头寸（流入 - 流出） */
    private BigDecimal netPosition;

    /** 当日大额支出笔数（超阈值） */
    private Integer largeOutflowCount;

    /** 当日大额支出金额 */
    private BigDecimal largeOutflowAmount;

    /** 统计账户数 */
    private Integer accountCount;
}
