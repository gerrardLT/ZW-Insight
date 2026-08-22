package com.zwinsight.budget.batch;

import com.alibaba.excel.EasyExcel;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.service.BudgetService;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.batch.domain.ImportResult;
import com.zwinsight.file.batch.dto.BudgetDetailExcelDTO;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * BudgetDetailBatchHandler 单元测试（P0 预算明细批量导入）
 * <p>
 * 使用真实 xlsx 走完整 EasyExcel 解析链路（不 mock 解析器），
 * 覆盖：正常追加（中文类别映射 + 合计缺省留空由服务计算）、budgetId 缺失拒绝、非法类别行级拒绝。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetDetailBatchHandlerTest {

    @TempDir
    Path tempDir;

    @Mock
    private BudgetService budgetService;

    @Mock
    private BizBudgetDetailMapper budgetDetailMapper;

    @InjectMocks
    private BudgetDetailBatchHandler handler;

    /** 用 EasyExcel 真实写 xlsx 后走监听器解析链路 */
    @SuppressWarnings("unchecked")
    private ImportResult runImport(Map<String, Object> extraParams, List<BudgetDetailExcelDTO> rows) throws Exception {
        Path file = tempDir.resolve("budget-detail-" + System.nanoTime() + ".xlsx");
        EasyExcel.write(file.toFile(), BudgetDetailExcelDTO.class).sheet().doWrite(rows);
        byte[] bytes = Files.readAllBytes(file);
        Files.delete(file);

        AbstractImportListener<BudgetDetailExcelDTO> listener =
                (AbstractImportListener<BudgetDetailExcelDTO>) handler.createImportListener(100L, extraParams);
        EasyExcel.read(new ByteArrayInputStream(bytes), BudgetDetailExcelDTO.class, listener)
                .sheet().doRead();
        return listener.getImportResult();
    }

    private BudgetDetailExcelDTO validRow() {
        BudgetDetailExcelDTO row = new BudgetDetailExcelDTO();
        row.setCostCategory("材料");
        row.setCostSubcategory("钢材");
        row.setItemName("螺纹钢");
        row.setSpecification("HRB400 φ20");
        row.setUnit("吨");
        row.setBudgetQuantity("10");
        row.setBudgetUnitPrice("4500");
        // 合计留空：由追加逻辑按 数量×单价 计算
        return row;
    }

    @Test
    @DisplayName("正常追加 - 中文类别映射枚举，合计缺省留空交由服务计算")
    @SuppressWarnings("unchecked")
    void happyPath_mapsCategoryAndDelegatesTotal() throws Exception {
        ImportResult result = runImport(Map.of("budgetId", "7"), List.of(validRow()));

        assertThat(result.isAllSuccess()).isTrue();
        assertThat(result.getSuccessRows()).isEqualTo(1);

        ArgumentCaptor<List<BizBudgetDetail>> captor = ArgumentCaptor.forClass(List.class);
        verify(budgetService).appendImportedDetails(eq(7L), captor.capture());
        List<BizBudgetDetail> details = captor.getValue();
        assertThat(details).hasSize(1);
        BizBudgetDetail detail = details.get(0);
        assertThat(detail.getCostCategory()).isEqualTo("MATERIAL");
        assertThat(detail.getItemName()).isEqualTo("螺纹钢");
        assertThat(detail.getBudgetQuantity()).isEqualByComparingTo("10");
        assertThat(detail.getBudgetUnitPrice()).isEqualByComparingTo("4500");
        assertThat(detail.getBudgetTotalPrice()).isNull();
    }

    @Test
    @DisplayName("未指定 budgetId - 拒绝创建监听器（不静默落库）")
    void missingBudgetId_rejected() {
        assertThatThrownBy(() -> handler.createImportListener(100L, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先选择要导入明细的预算");
    }

    @Test
    @DisplayName("budgetId 格式非法 - 抛出参数异常")
    void invalidBudgetId_rejected() {
        assertThatThrownBy(() -> handler.createImportListener(100L, Map.of("budgetId", "abc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("budgetId");
    }

    @Test
    @DisplayName("非法费用类别 - 行级拒绝且不触发追加")
    void invalidCategory_rowRejected() throws Exception {
        BudgetDetailExcelDTO row = validRow();
        row.setCostCategory("不存在的类别");

        ImportResult result = runImport(Map.of("budgetId", "7"), List.of(row));

        assertThat(result.getFailedRows()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getErrorMessage()).contains("费用类别错误");
        verify(budgetService, never()).appendImportedDetails(any(), any());
    }

    @Test
    @DisplayName("金额格式错误 - 行级拒绝")
    void invalidAmountFormat_rowRejected() throws Exception {
        BudgetDetailExcelDTO row = validRow();
        row.setBudgetQuantity("十吨");

        ImportResult result = runImport(Map.of("budgetId", "7"), List.of(row));

        assertThat(result.getFailedRows()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getErrorMessage()).contains("预算数量格式错误");
        verify(budgetService, never()).appendImportedDetails(any(), any());
    }
}
