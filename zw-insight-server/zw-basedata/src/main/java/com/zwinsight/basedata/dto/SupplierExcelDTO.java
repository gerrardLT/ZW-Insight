package com.zwinsight.basedata.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 供应商导入Excel DTO
 */
@Data
public class SupplierExcelDTO {

    @ExcelProperty("供应商名称")
    private String supplierName;

    @ExcelProperty("供应商编码")
    private String supplierCode;

    @ExcelProperty("供应商类型")
    private String supplierType;

    @ExcelProperty("联系人")
    private String contactName;

    @ExcelProperty("联系电话")
    private String contactPhone;

    @ExcelProperty("地址")
    private String address;

    @ExcelProperty("开户行")
    private String bankName;

    @ExcelProperty("银行账号")
    private String bankAccount;

    @ExcelProperty("税号")
    private String taxNumber;
}
