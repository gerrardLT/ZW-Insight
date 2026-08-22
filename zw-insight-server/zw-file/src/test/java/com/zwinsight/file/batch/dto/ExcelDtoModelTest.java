package com.zwinsight.file.batch.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批量导入 Excel DTO 模型测试（字段读写往返）
 */
class ExcelDtoModelTest {

    @Test
    @DisplayName("ProjectExcelDTO - 全字段读写往返")
    void projectDto() {
        ProjectExcelDTO dto = new ProjectExcelDTO();
        dto.setProjectName("滨江花园一期");
        dto.setProjectNature("房建");
        dto.setProjectType("住宅");
        dto.setOwnerCompanyName("业主单位");
        dto.setProjectAddress("杭州市滨江区");
        dto.setContactName("张三");
        dto.setContactPhone("13800000001");
        dto.setBudgetAmount("1000000.00");

        assertThat(dto.getProjectName()).isEqualTo("滨江花园一期");
        assertThat(dto.getProjectNature()).isEqualTo("房建");
        assertThat(dto.getProjectType()).isEqualTo("住宅");
        assertThat(dto.getOwnerCompanyName()).isEqualTo("业主单位");
        assertThat(dto.getProjectAddress()).isEqualTo("杭州市滨江区");
        assertThat(dto.getContactName()).isEqualTo("张三");
        assertThat(dto.getContactPhone()).isEqualTo("13800000001");
        assertThat(dto.getBudgetAmount()).isEqualTo("1000000.00");
    }

    @Test
    @DisplayName("ConstructionContractExcelDTO - 全字段读写往返")
    void constructionContractDto() {
        ConstructionContractExcelDTO dto = new ConstructionContractExcelDTO();
        dto.setProjectName("项目A");
        dto.setPartyAName("甲方");
        dto.setSigningDate("2026-01-10");
        dto.setStartDate("2026-02-01");
        dto.setEndDate("2026-12-31");
        dto.setContractAmount("5000000");
        dto.setTaxRate("0.09");

        assertThat(dto.getProjectName()).isEqualTo("项目A");
        assertThat(dto.getPartyAName()).isEqualTo("甲方");
        assertThat(dto.getSigningDate()).isEqualTo("2026-01-10");
        assertThat(dto.getStartDate()).isEqualTo("2026-02-01");
        assertThat(dto.getEndDate()).isEqualTo("2026-12-31");
        assertThat(dto.getContractAmount()).isEqualTo("5000000");
        assertThat(dto.getTaxRate()).isEqualTo("0.09");
    }

    @Test
    @DisplayName("LaborContractExcelDTO - 全字段读写往返")
    void laborContractDto() {
        LaborContractExcelDTO dto = new LaborContractExcelDTO();
        dto.setContractCode("LB-001");
        dto.setContractName("木工劳务合同");
        dto.setTeamName("木工一班");
        dto.setPartyAName("甲方");
        dto.setPartyBName("乙方");
        dto.setSigningDate("2026-03-01");
        dto.setStartDate("2026-03-05");
        dto.setEndDate("2026-11-30");
        dto.setContractAmount("800000");
        dto.setPaymentTerms("按月结算");

        assertThat(dto.getContractCode()).isEqualTo("LB-001");
        assertThat(dto.getContractName()).isEqualTo("木工劳务合同");
        assertThat(dto.getTeamName()).isEqualTo("木工一班");
        assertThat(dto.getPartyAName()).isEqualTo("甲方");
        assertThat(dto.getPartyBName()).isEqualTo("乙方");
        assertThat(dto.getSigningDate()).isEqualTo("2026-03-01");
        assertThat(dto.getStartDate()).isEqualTo("2026-03-05");
        assertThat(dto.getEndDate()).isEqualTo("2026-11-30");
        assertThat(dto.getContractAmount()).isEqualTo("800000");
        assertThat(dto.getPaymentTerms()).isEqualTo("按月结算");
    }

    @Test
    @DisplayName("BudgetDetailExcelDTO - 全字段读写往返")
    void budgetDetailDto() {
        BudgetDetailExcelDTO dto = new BudgetDetailExcelDTO();
        dto.setCostCategory("MATERIAL");
        dto.setCostSubcategory("钢材");
        dto.setItemName("螺纹钢");
        dto.setSpecification("HRB400");
        dto.setUnit("吨");
        dto.setBudgetQuantity("10");
        dto.setBudgetUnitPrice("4500");
        dto.setBudgetTotalPrice("45000");
        dto.setRemark("含税");

        assertThat(dto.getCostCategory()).isEqualTo("MATERIAL");
        assertThat(dto.getCostSubcategory()).isEqualTo("钢材");
        assertThat(dto.getItemName()).isEqualTo("螺纹钢");
        assertThat(dto.getSpecification()).isEqualTo("HRB400");
        assertThat(dto.getUnit()).isEqualTo("吨");
        assertThat(dto.getBudgetQuantity()).isEqualTo("10");
        assertThat(dto.getBudgetUnitPrice()).isEqualTo("4500");
        assertThat(dto.getBudgetTotalPrice()).isEqualTo("45000");
        assertThat(dto.getRemark()).isEqualTo("含税");
    }

    @Test
    @DisplayName("PayrollExcelDTO - 全字段读写往返")
    void payrollDto() {
        PayrollExcelDTO dto = new PayrollExcelDTO();
        dto.setTeamName("木工一班");
        dto.setOrderType("固定");
        dto.setPeriodStart("2026-04-01");
        dto.setPeriodEnd("2026-04-30");
        dto.setTeamId(66L);

        assertThat(dto.getTeamName()).isEqualTo("木工一班");
        assertThat(dto.getOrderType()).isEqualTo("固定");
        assertThat(dto.getPeriodStart()).isEqualTo("2026-04-01");
        assertThat(dto.getPeriodEnd()).isEqualTo("2026-04-30");
        assertThat(dto.getTeamId()).isEqualTo(66L);
    }

    @Test
    @DisplayName("StockExcelDTO - 全字段读写往返")
    void stockDto() {
        StockExcelDTO dto = new StockExcelDTO();
        dto.setMaterialName("螺纹钢");
        dto.setSpecification("HRB400 φ20");
        dto.setUnit("吨");
        dto.setStockQuantity("5.5");
        dto.setMinStock("10");
        dto.setProjectName("项目A");
        dto.setWarningStatus("WARNING");

        assertThat(dto.getMaterialName()).isEqualTo("螺纹钢");
        assertThat(dto.getSpecification()).isEqualTo("HRB400 φ20");
        assertThat(dto.getUnit()).isEqualTo("吨");
        assertThat(dto.getStockQuantity()).isEqualTo("5.5");
        assertThat(dto.getMinStock()).isEqualTo("10");
        assertThat(dto.getProjectName()).isEqualTo("项目A");
        assertThat(dto.getWarningStatus()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("PaymentReceivedExcelDTO - 全字段读写往返")
    void paymentReceivedDto() {
        PaymentReceivedExcelDTO dto = new PaymentReceivedExcelDTO();
        dto.setProjectName("项目A");
        dto.setReceiveDate("2026-05-20");
        dto.setReceiveAmount("1000000");
        dto.setReceiver("财务部");
        dto.setReceiveType("银行转账");
        dto.setReceiveBankAccount("6222000000000001");
        dto.setStatus("CONFIRMED");

        assertThat(dto.getProjectName()).isEqualTo("项目A");
        assertThat(dto.getReceiveDate()).isEqualTo("2026-05-20");
        assertThat(dto.getReceiveAmount()).isEqualTo("1000000");
        assertThat(dto.getReceiver()).isEqualTo("财务部");
        assertThat(dto.getReceiveType()).isEqualTo("银行转账");
        assertThat(dto.getReceiveBankAccount()).isEqualTo("6222000000000001");
        assertThat(dto.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("OutputReportExcelDTO - 全字段读写往返")
    void outputReportDto() {
        OutputReportExcelDTO dto = new OutputReportExcelDTO();
        dto.setProjectName("项目A");
        dto.setReportPeriod("2026-04");
        dto.setCurrentOutput("100.50");
        dto.setCumulativeOutput("320.00");
        dto.setConfirmDate("2026-04-30");
        dto.setStatus("已审批");

        assertThat(dto.getProjectName()).isEqualTo("项目A");
        assertThat(dto.getReportPeriod()).isEqualTo("2026-04");
        assertThat(dto.getCurrentOutput()).isEqualTo("100.50");
        assertThat(dto.getCumulativeOutput()).isEqualTo("320.00");
        assertThat(dto.getConfirmDate()).isEqualTo("2026-04-30");
        assertThat(dto.getStatus()).isEqualTo("已审批");
    }
}
