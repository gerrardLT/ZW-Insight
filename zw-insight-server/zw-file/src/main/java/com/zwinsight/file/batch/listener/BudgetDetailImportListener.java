package com.zwinsight.file.batch.listener;

import cn.hutool.core.util.StrUtil;
import com.zwinsight.file.batch.dto.BudgetDetailExcelDTO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 预算明细导入监听器
 * <p>
 * 费用类别支持中文或枚举值录入，统一映射为枚举值落库。
 * 预算合计缺省时由保存逻辑按 数量×单价 计算。
 * </p>
 */
@Slf4j
public class BudgetDetailImportListener extends AbstractImportListener<BudgetDetailExcelDTO> {

    private final Consumer<List<BudgetDetailExcelDTO>> batchSaveAction;

    /** 中文费用类别 → 枚举值映射 */
    private static final Map<String, String> CATEGORY_MAP = Map.of(
            "材料", "MATERIAL",
            "人工", "LABOR",
            "机械", "MACHINE",
            "分包", "SUBCONTRACT",
            "间接费", "INDIRECT",
            "其他", "OTHER"
    );

    /**
     * @param batchSaveAction 批量保存动作
     */
    public BudgetDetailImportListener(Consumer<List<BudgetDetailExcelDTO>> batchSaveAction) {
        this.batchSaveAction = batchSaveAction;
    }

    @Override
    protected String validate(BudgetDetailExcelDTO data) {
        if (StrUtil.isBlank(data.getItemName())) {
            return "项目名称不能为空";
        }
        if (StrUtil.isBlank(data.getCostCategory())) {
            return "费用类别不能为空";
        }
        String category = mapCategory(data.getCostCategory().trim());
        if (category == null) {
            return "费用类别错误，应为 材料/人工/机械/分包/间接费/其他";
        }
        data.setCostCategory(category);

        BigDecimal quantity = parseAmount(data.getBudgetQuantity());
        if (quantity == null) {
            return "预算数量格式错误";
        }
        BigDecimal price = parseAmount(data.getBudgetUnitPrice());
        if (price == null) {
            return "预算单价格式错误";
        }
        if (StrUtil.isNotBlank(data.getBudgetTotalPrice()) && parseAmount(data.getBudgetTotalPrice()) == null) {
            return "预算合计格式错误";
        }
        return null;
    }

    private BigDecimal parseAmount(String value) {
        if (StrUtil.isBlank(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 费用类别映射（兼容枚举值直填）
     */
    private String mapCategory(String input) {
        String mapped = CATEGORY_MAP.get(input);
        if (mapped != null) {
            return mapped;
        }
        if (CATEGORY_MAP.containsValue(input)) {
            return input;
        }
        return null;
    }

    @Override
    protected void batchSave(List<BudgetDetailExcelDTO> dataList) {
        batchSaveAction.accept(dataList);
        log.info("预算明细批量导入 {} 条", dataList.size());
    }
}
