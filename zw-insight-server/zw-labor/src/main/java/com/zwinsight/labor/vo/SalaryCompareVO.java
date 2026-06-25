package com.zwinsight.labor.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 薪资同比环比数据 VO
 */
@Data
public class SalaryCompareVO {

    /** 当前月份 */
    private String currentMonth;

    /** 当月总薪资 */
    private BigDecimal currentAmount;

    /** 上月总薪资 */
    private BigDecimal previousMonthAmount;

    /** 去年同月总薪资 */
    private BigDecimal lastYearAmount;

    /** 环比变化率（%，精确到小数点后1位） */
    private BigDecimal momRate;

    /** 同比变化率（%，精确到小数点后1位） */
    private BigDecimal yoyRate;

    /** 当月自有劳务薪资 */
    private BigDecimal currentFixedAmount;

    /** 当月零星用工薪资 */
    private BigDecimal currentTemporaryAmount;

    /** 上月自有劳务薪资 */
    private BigDecimal previousFixedAmount;

    /** 上月零星用工薪资 */
    private BigDecimal previousTemporaryAmount;

    /** 去年同月自有劳务薪资 */
    private BigDecimal lastYearFixedAmount;

    /** 去年同月零星用工薪资 */
    private BigDecimal lastYearTemporaryAmount;
}
