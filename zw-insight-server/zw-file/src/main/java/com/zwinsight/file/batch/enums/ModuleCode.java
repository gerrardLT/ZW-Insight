package com.zwinsight.file.batch.enums;

import lombok.Getter;

/**
 * 批量导入导出模块编码
 */
@Getter
public enum ModuleCode {

    MACHINE_LEDGER("MACHINE_LEDGER", "机械台账", "机械台账导入模板.xlsx"),
    LABOR_ROSTER("LABOR_ROSTER", "劳务花名册", "劳务花名册导入模板.xlsx"),
    SYS_USER("SYS_USER", "系统用户", "系统用户导入模板.xlsx"),
    SUPPLIER("SUPPLIER", "供应商", "供应商导入模板.xlsx"),
    MATERIAL("MATERIAL", "材料字典", "材料字典导入模板.xlsx"),
    PROJECT("PROJECT", "项目报备", "项目报备导入模板.xlsx"),
    CONTRACT("CONTRACT", "施工合同", "施工合同导入模板.xlsx"),
    PAYROLL("PAYROLL", "工资单", "工资单导入模板.xlsx"),
    BUDGET_DETAIL("BUDGET_DETAIL", "预算明细", "预算明细导入模板.xlsx"),
    LABOR_CONTRACT("LABOR_CONTRACT", "劳务合同", "劳务合同导入模板.xlsx"),
    OUTPUT_REPORT("OUTPUT_REPORT", "产值上报", "产值上报导出.xlsx"),
    STOCK("STOCK", "库存查询", "库存查询导出.xlsx"),
    PAYMENT_RECEIVED("PAYMENT_RECEIVED", "回款登记", "回款登记导出.xlsx");

    /**
     * 模块编码
     */
    private final String code;

    /**
     * 模块名称
     */
    private final String name;

    /**
     * 导入模板文件名
     */
    private final String templateFileName;

    ModuleCode(String code, String name, String templateFileName) {
        this.code = code;
        this.name = name;
        this.templateFileName = templateFileName;
    }

    /**
     * 根据编码获取枚举
     */
    public static ModuleCode fromCode(String code) {
        for (ModuleCode mc : values()) {
            if (mc.getCode().equals(code)) {
                return mc;
            }
        }
        throw new IllegalArgumentException("不支持的模块编码: " + code);
    }
}
