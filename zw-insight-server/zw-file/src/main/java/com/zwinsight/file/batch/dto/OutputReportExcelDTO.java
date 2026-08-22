package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 产值上报导出 Excel DTO（仅导出，不支持导入）
 */
@Data
public class OutputReportExcelDTO {

    @ExcelProperty("项目名称")
    private String projectName;

    @ExcelProperty("报告期间")
    private String reportPeriod;

    @ExcelProperty("本期产值")
    private String currentOutput;

    @ExcelProperty("累计产值")
    private String cumulativeOutput;

    @ExcelProperty("确认日期")
    private String confirmDate;

    @ExcelProperty("状态")
    private String status;
}
