package com.zwinsight.budget.batch;

import com.alibaba.excel.EasyExcel;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.domain.BizBudgetDetail;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.service.BudgetService;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.batch.domain.ImportResult;
import com.zwinsight.file.batch.dto.BudgetDetailExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
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
import java.math.BigDecimal;
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
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("supports/getImportDtoClass - 仅支持 BUDGET_DETAIL 模块")
    void supportsAndDtoClass() {
        assertThat(handler.supports(ModuleCode.BUDGET_DETAIL)).isTrue();
        assertThat(handler.supports(ModuleCode.PROJECT)).isFalse();
        assertThat(handler.getImportDtoClass()).isEqualTo(BudgetDetailExcelDTO.class);
    }

    @Test
    @DisplayName("单参 createImportListener - 无 extraParams 拒绝创建（不静默落库）")
    void singleArgListener_missingBudgetId_rejected() {
        assertThatThrownBy(() -> handler.createImportListener(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先选择要导入明细的预算");
        assertThatThrownBy(() -> handler.createImportListener(100L, null))
                .isInstanceOf(BusinessException.class);
    }

    private BizBudgetDetail detail(String category, BigDecimal qty, BigDecimal price, BigDecimal total) {
        BizBudgetDetail detail = new BizBudgetDetail();
        detail.setCostCategory(category);
        detail.setCostSubcategory("钢材");
        detail.setItemName("螺纹钢");
        detail.setSpecification("HRB400");
        detail.setUnit("吨");
        detail.setBudgetQuantity(qty);
        detail.setBudgetUnitPrice(price);
        detail.setBudgetTotalPrice(total);
        detail.setRemark("含税");
        return detail;
    }

    @Test
    @DisplayName("queryExportData - 按 budgetId 导出（枚举类别转中文，未知/null 类别透传，金额转字符串）")
    void queryExportData_byBudgetId() {
        when(budgetDetailMapper.selectList(any())).thenReturn(List.of(
                detail("MATERIAL", new BigDecimal("10"), new BigDecimal("4500"), new BigDecimal("45000")),
                detail("UNKNOWN_CAT", null, null, null),
                detail(null, null, null, null)
        ));

        List<?> result = handler.queryExportData(Map.of("budgetId", "7"));

        assertThat(result).hasSize(3);
        BudgetDetailExcelDTO dto = (BudgetDetailExcelDTO) result.get(0);
        assertThat(dto.getCostCategory()).isEqualTo("材料");
        assertThat(dto.getItemName()).isEqualTo("螺纹钢");
        assertThat(dto.getBudgetQuantity()).isEqualTo("10");
        assertThat(dto.getBudgetUnitPrice()).isEqualTo("4500");
        assertThat(dto.getBudgetTotalPrice()).isEqualTo("45000");
        BudgetDetailExcelDTO unknown = (BudgetDetailExcelDTO) result.get(1);
        assertThat(unknown.getCostCategory()).isEqualTo("UNKNOWN_CAT");
        assertThat(unknown.getBudgetQuantity()).isEmpty();
        assertThat(((BudgetDetailExcelDTO) result.get(2)).getCostCategory()).isEmpty();
    }

    @Test
    @DisplayName("queryExportData - 各类别枚举转中文标签全覆盖")
    void queryExportData_allCategoryLabels() {
        when(budgetDetailMapper.selectList(any())).thenReturn(List.of(
                detail("LABOR", null, null, null),
                detail("MACHINE", null, null, null),
                detail("SUBCONTRACT", null, null, null),
                detail("INDIRECT", null, null, null),
                detail("OTHER", null, null, null)
        ));

        List<?> result = handler.queryExportData(Map.of("budgetId", "7"));

        assertThat(result.stream().map(r -> ((BudgetDetailExcelDTO) r).getCostCategory()).toList())
                .containsExactly("人工", "机械", "分包", "间接费", "其他");
    }

    @Test
    @DisplayName("queryExportData - 仅传 projectId 时回退查项目预算；预算不存在返回空列表")
    void queryExportData_fallbackToProjectBudget() {
        BizBudget budget = new BizBudget();
        budget.setId(88L);
        when(budgetService.getByProject(10L)).thenReturn(budget);
        when(budgetDetailMapper.selectList(any())).thenReturn(List.of(detail("MATERIAL", null, null, null)));

        assertThat(handler.queryExportData(Map.of("projectId", "10"))).hasSize(1);
        verify(budgetService).getByProject(10L);
    }

    @Test
    @DisplayName("queryExportData - 项目无预算/参数缺失/params 为 null 均返回空列表")
    void queryExportData_emptyCases() {
        when(budgetService.getByProject(99L)).thenReturn(null);
        assertThat(handler.queryExportData(Map.of("projectId", "99"))).isEmpty();
        assertThat(handler.queryExportData(Map.of())).isEmpty();
        assertThat(handler.queryExportData(null)).isEmpty();
    }
}
