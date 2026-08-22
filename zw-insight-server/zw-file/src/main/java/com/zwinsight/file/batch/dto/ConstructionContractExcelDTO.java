package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 施工合同导入 Excel DTO
 */
@Data
public class ConstructionContractExcelDTO {

    @ExcelProperty("项目名称")
    private String projectName;

    @ExcelProperty("甲方名称")
    private String partyAName;

    @ExcelProperty("签订日期")
    private String signingDate;

    @ExcelProperty("开工日期")
    private String startDate;

    @ExcelProperty("竣工日期")
    private String endDate;

    @ExcelProperty("合同金额")
    private String contractAmount;

    @ExcelProperty("税率(%)")
    private String taxRate;
}
