package com.zwinsight.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算编制创建/编辑请求 DTO
 */
@Data
public class BudgetCreateRequest {

    /**
     * 项目ID
     */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /**
     * 预算类型（ORIGINAL-编制/CHANGE-变更）
     */
    @NotBlank(message = "预算类型不能为空")
    private String budgetType;

    /**
     * 预算总额
     */
    private BigDecimal totalAmount;

    /**
     * 预算明细行（科目额度）。
     * 2026-08-17 归零重建全链路 E2E 缺陷#8：预算管控按科目检查额度，
     * 但系统无明细录入入口，BLOCK 模式下任何支出合同都被“该科目未设置预算额度”拦截。
     */
    private List<DetailItem> details;

    /**
     * 预算明细行 DTO
     */
    @Data
    public static class DetailItem {
        /** 费用类别（MATERIAL/LABOR/MACHINE/SUBCONTRACT/INDIRECT/OTHER） */
        private String costCategory;
        /** 费用子类 */
        private String costSubcategory;
        /** 项目名称 */
        private String itemName;
        /** 规格 */
        private String specification;
        /** 单位 */
        private String unit;
        /** 预算数量 */
        private BigDecimal budgetQuantity;
        /** 预算单价 */
        private BigDecimal budgetUnitPrice;
        /** 预算合计（缺省时按 数量×单价 计算） */
        private BigDecimal budgetTotalPrice;
        /** 备注 */
        private String remark;
    }
}
