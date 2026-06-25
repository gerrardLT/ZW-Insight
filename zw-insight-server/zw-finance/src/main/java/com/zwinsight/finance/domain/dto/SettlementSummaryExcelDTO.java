package com.zwinsight.finance.domain.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 结算报告 - 收支汇总表 Excel 导出 DTO
 */
@Data
@ColumnWidth(20)
public class SettlementSummaryExcelDTO {

    @ExcelProperty("结算单编号")
    @ColumnWidth(25)
    private String settlementCode;

    @ExcelProperty("项目名称")
    @ColumnWidth(30)
    private String projectName;

    // ===== 收入汇总 =====

    @ExcelProperty("施工合同总额")
    private BigDecimal constructionContractAmount;

    @ExcelProperty("累计产值")
    private BigDecimal cumulativeOutput;

    @ExcelProperty("累计收款")
    private BigDecimal cumulativeReceived;

    @ExcelProperty("累计开票")
    private BigDecimal cumulativeInvoiced;

    @ExcelProperty("总收入")
    private BigDecimal totalIncome;

    // ===== 支出汇总 =====

    @ExcelProperty("分包结算")
    private BigDecimal subcontractSettled;

    @ExcelProperty("劳务结算")
    private BigDecimal laborSettled;

    @ExcelProperty("材料结算")
    private BigDecimal materialSettled;

    @ExcelProperty("机械结算")
    private BigDecimal machineSettled;

    @ExcelProperty("累计付款")
    private BigDecimal cumulativePaid;

    @ExcelProperty("总支出")
    private BigDecimal totalExpenditure;

    // ===== 利润 =====

    @ExcelProperty("利润")
    private BigDecimal profit;

    @ExcelProperty("利润率(%)")
    private BigDecimal profitRate;
}
