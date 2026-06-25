package com.zwinsight.finance.domain.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 结算报告 - 合同结算明细 Excel 导出 DTO
 */
@Data
@ColumnWidth(18)
public class SettlementDetailExcelDTO {

    @ExcelProperty("合同类型")
    private String contractType;

    @ExcelProperty("合同编号")
    @ColumnWidth(25)
    private String contractCode;

    @ExcelProperty("合同名称")
    @ColumnWidth(30)
    private String contractName;

    @ExcelProperty("合同金额")
    private BigDecimal contractAmount;

    @ExcelProperty("已结算金额")
    private BigDecimal settledAmount;

    @ExcelProperty("已付金额")
    private BigDecimal paidAmount;

    @ExcelProperty("未结金额")
    private BigDecimal unsettledAmount;

    @ExcelProperty("结算状态")
    private String settlementStatus;
}
