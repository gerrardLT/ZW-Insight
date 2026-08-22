package com.zwinsight.budget.batch;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.service.BudgetService;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.batch.dto.BudgetDetailExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.BudgetDetailImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 预算明细批量导入导出处理器
 * <p>
 * 导入需通过 extraParams 指定 budgetId，明细追加走
 * {@link BudgetService#appendImportedDetails}（仅草稿状态可追加 + 总额回写）。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class BudgetDetailBatchHandler implements BatchModuleHandler {

    private final BudgetService budgetService;
    private final BizBudgetDetailMapper budgetDetailMapper;

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.BUDGET_DETAIL == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return BudgetDetailExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        return createImportListener(projectId, Map.of());
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId, Map<String, Object> extraParams) {
        Long budgetId = parseBudgetId(extraParams);
        return new BudgetDetailImportListener(
                // 批量保存：DTO → 明细实体，走 BudgetService 追加（草稿门禁 + 总额回写）
                dataList -> {
                    List<BizBudgetDetail> details = new ArrayList<>();
                    for (BudgetDetailExcelDTO dto : dataList) {
                        BizBudgetDetail detail = new BizBudgetDetail();
                        detail.setCostCategory(dto.getCostCategory());
                        detail.setCostSubcategory(StrUtil.trimToNull(dto.getCostSubcategory()));
                        detail.setItemName(dto.getItemName().trim());
                        detail.setSpecification(StrUtil.trimToNull(dto.getSpecification()));
                        detail.setUnit(StrUtil.trimToNull(dto.getUnit()));
                        detail.setBudgetQuantity(parseAmount(dto.getBudgetQuantity()));
                        detail.setBudgetUnitPrice(parseAmount(dto.getBudgetUnitPrice()));
                        detail.setBudgetTotalPrice(parseAmount(dto.getBudgetTotalPrice()));
                        detail.setRemark(StrUtil.trimToNull(dto.getRemark()));
                        details.add(detail);
                    }
                    budgetService.appendImportedDetails(budgetId, details);
                }
        );
    }

    /**
     * 从额外参数解析预算ID（导入明细必须指定预算，缺失/非法直接报错，不静默落库）
     */
    private Long parseBudgetId(Map<String, Object> extraParams) {
        Object value = extraParams == null ? null : extraParams.get("budgetId");
        if (value == null || StrUtil.isBlank(value.toString())) {
            throw new BusinessException("请先选择要导入明细的预算");
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("budgetId 参数格式非法: " + value);
        }
    }

    private BigDecimal parseAmount(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        Long budgetId = resolveExportBudgetId(params);
        if (budgetId == null) {
            return List.of();
        }
        List<BizBudgetDetail> list = budgetDetailMapper.selectList(
                new LambdaQueryWrapper<BizBudgetDetail>()
                        .eq(BizBudgetDetail::getBudgetId, budgetId)
                        .orderByAsc(BizBudgetDetail::getId)
        );
        return list.stream().map(detail -> {
            BudgetDetailExcelDTO dto = new BudgetDetailExcelDTO();
            dto.setCostCategory(categoryLabel(detail.getCostCategory()));
            dto.setCostSubcategory(detail.getCostSubcategory());
            dto.setItemName(detail.getItemName());
            dto.setSpecification(detail.getSpecification());
            dto.setUnit(detail.getUnit());
            dto.setBudgetQuantity(detail.getBudgetQuantity() != null ? detail.getBudgetQuantity().toPlainString() : "");
            dto.setBudgetUnitPrice(detail.getBudgetUnitPrice() != null ? detail.getBudgetUnitPrice().toPlainString() : "");
            dto.setBudgetTotalPrice(detail.getBudgetTotalPrice() != null ? detail.getBudgetTotalPrice().toPlainString() : "");
            dto.setRemark(detail.getRemark());
            return dto;
        }).toList();
    }

    /**
     * 解析导出目标预算：优先 budgetId 参数，其次按 projectId 取原始预算
     */
    private Long resolveExportBudgetId(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        if (params.get("budgetId") != null && StrUtil.isNotBlank(params.get("budgetId").toString())) {
            return Long.valueOf(params.get("budgetId").toString().trim());
        }
        if (params.get("projectId") != null && StrUtil.isNotBlank(params.get("projectId").toString())) {
            BizBudget budget = budgetService.getByProject(Long.valueOf(params.get("projectId").toString().trim()));
            return budget != null ? budget.getId() : null;
        }
        return null;
    }

    private String categoryLabel(String category) {
        if (category == null) {
            return "";
        }
        return switch (category) {
            case "MATERIAL" -> "材料";
            case "LABOR" -> "人工";
            case "MACHINE" -> "机械";
            case "SUBCONTRACT" -> "分包";
            case "INDIRECT" -> "间接费";
            case "OTHER" -> "其他";
            default -> category;
        };
    }
}
