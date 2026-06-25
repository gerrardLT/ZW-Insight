package com.zwinsight.labor.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 薪资汇总表 Excel 导出 DTO（Sheet1）
 */
@Data
public class SalarySummaryExcelDTO {

    @ExcelProperty("班组名称")
    private String teamName;

    @ExcelProperty("班组长")
    private String leaderName;

    @ExcelProperty("用工类型")
    private String orderTypeLabel;

    @ExcelProperty("人数")
    private Integer headCount;

    @ExcelProperty("应发总额")
    @NumberFormat("#,##0.00")
    private BigDecimal totalPayable;

    @ExcelProperty("扣款总额")
    @NumberFormat("#,##0.00")
    private BigDecimal totalDeduction;

    @ExcelProperty("实发总额")
    @NumberFormat("#,##0.00")
    private BigDecimal totalActual;
}
