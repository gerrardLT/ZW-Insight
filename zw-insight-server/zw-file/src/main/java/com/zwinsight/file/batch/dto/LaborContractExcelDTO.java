package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 劳务合同导入 Excel DTO
 */
@Data
public class LaborContractExcelDTO {

    @ExcelProperty("合同编号")
    private String contractCode;

    @ExcelProperty("合同名称")
    private String contractName;

    @ExcelProperty("施工队伍")
    private String teamName;

    @ExcelProperty("甲方名称")
    private String partyAName;

    @ExcelProperty("乙方名称")
    private String partyBName;

    @ExcelProperty("签订日期")
    private String signingDate;

    @ExcelProperty("开始日期")
    private String startDate;

    @ExcelProperty("结束日期")
    private String endDate;

    @ExcelProperty("合同金额")
    private String contractAmount;

    @ExcelProperty("付款条款")
    private String paymentTerms;
}
