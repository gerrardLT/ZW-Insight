package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 机械台账导入 Excel DTO
 */
@Data
public class MachineLedgerExcelDTO {

    @ExcelProperty("机械名称")
    private String machineName;

    @ExcelProperty("机械编号")
    private String machineCode;

    @ExcelProperty("机械类型")
    private String machineType;

    @ExcelProperty("品牌")
    private String brand;

    @ExcelProperty("规格型号")
    private String specification;

    @ExcelProperty("权属")
    private String ownerType;

    @ExcelProperty("当前项目")
    private String currentProject;

    @ExcelProperty("购置日期")
    private String purchaseDate;
}
