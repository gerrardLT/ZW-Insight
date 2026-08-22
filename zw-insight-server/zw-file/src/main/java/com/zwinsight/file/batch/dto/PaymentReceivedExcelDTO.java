package com.zwinsight.file.batch.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 回款登记导出 Excel DTO（仅导出，不支持导入）
 */
@Data
public class PaymentReceivedExcelDTO {

    @ExcelProperty("项目名称")
    private String projectName;

    @ExcelProperty("收款日期")
    private String receiveDate;

    @ExcelProperty("收款金额")
    private String receiveAmount;

    @ExcelProperty("收款人")
    private String receiver;

    @ExcelProperty("收款方式")
    private String receiveType;

    @ExcelProperty("收款银行账号")
    private String receiveBankAccount;

    @ExcelProperty("状态")
    private String status;
}
