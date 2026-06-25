package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 供应商导入 Excel DTO
 */
@Data
public class SupplierExcelDTO {

    @ExcelProperty("供应商名称")
    private String supplierName;

    @ExcelProperty("统一社会信用代码")
    private String creditCode;

    @ExcelProperty("联系人")
    private String contactPerson;

    @ExcelProperty("联系电话")
    private String contactPhone;

    @ExcelProperty("经营范围")
    private String businessScope;

    @ExcelProperty("地址")
    private String address;
}
