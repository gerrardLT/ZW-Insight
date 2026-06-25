package com.zwinsight.labor.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 薪资明细表 Excel 导出 DTO（Sheet2）
 */
@Data
public class SalaryDetailExcelDTO {

    @ExcelProperty("班组名称")
    private String teamName;

    @ExcelProperty("工人姓名")
    private String workerName;

    @ExcelProperty("身份证后4位")
    private String idCardLast4;

    @ExcelProperty("用工类型")
    private String orderTypeLabel;

    @ExcelProperty("出勤天数")
    private Integer attendanceDays;

    @ExcelProperty("加班工时")
    @NumberFormat("#,##0.0")
    private BigDecimal overtimeHours;

    @ExcelProperty("应发金额")
    @NumberFormat("#,##0.00")
    private BigDecimal payable;

    @ExcelProperty("扣款金额")
    @NumberFormat("#,##0.00")
    private BigDecimal deduction;

    @ExcelProperty("实发金额")
    @NumberFormat("#,##0.00")
    private BigDecimal actual;
}
