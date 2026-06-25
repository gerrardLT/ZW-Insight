package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 劳务花名册导入 Excel DTO
 */
@Data
public class LaborRosterExcelDTO {

    @ExcelProperty("工人姓名")
    private String workerName;

    @ExcelProperty("身份证号")
    private String idCard;

    @ExcelProperty("联系电话")
    private String phone;

    @ExcelProperty("用工类型")
    private String workerType;

    @ExcelProperty("班组名称")
    private String teamName;

    @ExcelProperty("工种")
    private String workType;

    @ExcelProperty("入场日期")
    private String entryDate;
}
