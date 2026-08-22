package com.zwinsight.file.batch.listener;

import com.zwinsight.file.batch.dto.BudgetDetailExcelDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BudgetDetailImportListener 单元测试（预算明细导入校验与费用类别映射）
 */
class BudgetDetailImportListenerTest {

    private BudgetDetailExcelDTO validRow() {
        BudgetDetailExcelDTO row = new BudgetDetailExcelDTO();
        row.setItemName("螺纹钢");
        row.setCostCategory("材料");
        row.setBudgetQuantity("10");
        row.setBudgetUnitPrice("4500");
        return row;
    }

    private String firstError(BudgetDetailImportListener listener, BudgetDetailExcelDTO row) {
        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);
        return listener.getImportResult().getErrors().get(0).getErrorMessage();
    }

    @Test
    @DisplayName("项目名称为空 - 行级拒绝")
    void blankItemName_rejected() {
        BudgetDetailImportListener listener = new BudgetDetailImportListener(list -> { });
        BudgetDetailExcelDTO row = validRow();
        row.setItemName(" ");
        assertThat(firstError(listener, row)).contains("项目名称不能为空");
    }

    @Test
    @DisplayName("费用类别为空/非法 - 分别行级拒绝")
    void categoryValidation_rejected() {
        BudgetDetailExcelDTO blank = validRow();
        blank.setCostCategory(null);
        assertThat(firstError(new BudgetDetailImportListener(list -> { }), blank)).contains("费用类别不能为空");

        BudgetDetailExcelDTO invalid = validRow();
        invalid.setCostCategory("管理费");
        assertThat(firstError(new BudgetDetailImportListener(list -> { }), invalid)).contains("费用类别错误");
    }

    @Test
    @DisplayName("中文类别与枚举值直填 - 均映射为枚举值落库")
    void categoryMapping_chineseAndEnum() {
        List<BudgetDetailExcelDTO> saved = new ArrayList<>();
        BudgetDetailImportListener listener = new BudgetDetailImportListener(saved::addAll);

        BudgetDetailExcelDTO chinese = validRow();
        chinese.setCostCategory("人工");
        listener.invoke(chinese, null);

        BudgetDetailExcelDTO enumDirect = validRow();
        enumDirect.setCostCategory("MACHINE");
        listener.invoke(enumDirect, null);

        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().isAllSuccess()).isTrue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getCostCategory()).isEqualTo("LABOR");
        assertThat(saved.get(1).getCostCategory()).isEqualTo("MACHINE");
    }

    @Test
    @DisplayName("数量/单价/合计格式错误 - 分别行级拒绝")
    void amountFormatValidation_rejected() {
        BudgetDetailExcelDTO badQty = validRow();
        badQty.setBudgetQuantity("十吨");
        assertThat(firstError(new BudgetDetailImportListener(list -> { }), badQty)).contains("预算数量格式错误");

        BudgetDetailExcelDTO badPrice = validRow();
        badPrice.setBudgetUnitPrice("面议");
        assertThat(firstError(new BudgetDetailImportListener(list -> { }), badPrice)).contains("预算单价格式错误");

        BudgetDetailExcelDTO badTotal = validRow();
        badTotal.setBudgetTotalPrice("约五万");
        assertThat(firstError(new BudgetDetailImportListener(list -> { }), badTotal)).contains("预算合计格式错误");
    }

    @Test
    @DisplayName("数量/单价缺省 - 按零值放行（合计由保存逻辑计算）")
    void blankAmounts_treatedAsZero() {
        List<BudgetDetailExcelDTO> saved = new ArrayList<>();
        BudgetDetailImportListener listener = new BudgetDetailImportListener(saved::addAll);
        BudgetDetailExcelDTO row = validRow();
        row.setBudgetQuantity(null);
        row.setBudgetUnitPrice("");

        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().isAllSuccess()).isTrue();
        assertThat(saved).hasSize(1);
    }
}
