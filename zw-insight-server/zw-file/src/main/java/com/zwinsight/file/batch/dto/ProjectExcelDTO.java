package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 项目报备导入 Excel DTO
 */
@Data
public class ProjectExcelDTO {

    @ExcelProperty("项目名称")
    private String projectName;

    @ExcelProperty("项目性质")
    private String projectNature;

    @ExcelProperty("项目类型")
    private String projectType;

    @ExcelProperty("业主单位")
    private String ownerCompanyName;

    @ExcelProperty("项目地址")
    private String projectAddress;

    @ExcelProperty("联系人")
    private String contactName;

    @ExcelProperty("联系电话")
    private String contactPhone;

    @ExcelProperty("预算金额")
    private String budgetAmount;
}
