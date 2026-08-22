package com.zwinsight.material.batch;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.batch.dto.StockExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.service.ProjectMaterialStockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * StockBatchHandler 单元测试（库存查询导出处理器）
 */
@ExtendWith(MockitoExtension.class)
class StockBatchHandlerTest {

    @Mock
    private ProjectMaterialStockService stockService;

    @InjectMocks
    private StockBatchHandler handler;

    @Test
    @DisplayName("supports - 仅支持 STOCK 模块")
    void supports_onlyStock() {
        assertThat(handler.supports(ModuleCode.STOCK)).isTrue();
        ModuleCode other = Arrays.stream(ModuleCode.values())
                .filter(c -> c != ModuleCode.STOCK)
                .findFirst()
                .orElseThrow();
        assertThat(handler.supports(other)).isFalse();
    }

    @Test
    @DisplayName("createImportListener - 库存查询不支持导入，抛业务异常")
    void createImportListener_throws() {
        assertThatThrownBy(() -> handler.createImportListener(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持批量导入");
    }

    @Test
    @DisplayName("queryExportData - 低于安全库存标记为库存不足")
    void queryExportData_lowStockMarked() {
        BizProjectMaterialStock low = new BizProjectMaterialStock();
        low.setMaterialName("螺纹钢");
        low.setSpecification("HRB400 12mm");
        low.setUnit("吨");
        low.setStockQuantity(new BigDecimal("2"));
        low.setMinStock(new BigDecimal("5"));
        low.setProjectName("测试项目");

        BizProjectMaterialStock normal = new BizProjectMaterialStock();
        normal.setMaterialName("水泥");
        normal.setStockQuantity(new BigDecimal("100"));
        normal.setMinStock(new BigDecimal("50"));

        when(stockService.listForExport(eq(10L), eq("钢"), isNull(), isNull()))
                .thenReturn(Arrays.asList(low, normal));

        List<?> result = handler.queryExportData(Map.of("projectId", "10", "materialName", "钢"));

        assertThat(result).hasSize(2);
        StockExcelDTO lowDto = (StockExcelDTO) result.get(0);
        assertThat(lowDto.getMaterialName()).isEqualTo("螺纹钢");
        assertThat(lowDto.getStockQuantity()).isEqualTo("2");
        assertThat(lowDto.getMinStock()).isEqualTo("5");
        assertThat(lowDto.getWarningStatus()).isEqualTo("库存不足");
        StockExcelDTO normalDto = (StockExcelDTO) result.get(1);
        assertThat(normalDto.getWarningStatus()).isEqualTo("正常");
    }

    @Test
    @DisplayName("queryExportData - 未配置安全库存视为正常，数量为空导出空串")
    void queryExportData_noMinStockIsNormal() {
        BizProjectMaterialStock stock = new BizProjectMaterialStock();
        stock.setMaterialName("砂石");
        when(stockService.listForExport(isNull(), isNull(), isNull(), eq("LOW")))
                .thenReturn(Collections.singletonList(stock));

        List<?> result = handler.queryExportData(Map.of("warning", "LOW"));

        StockExcelDTO dto = (StockExcelDTO) result.get(0);
        assertThat(dto.getWarningStatus()).isEqualTo("正常");
        assertThat(dto.getStockQuantity()).isEmpty();
        assertThat(dto.getMinStock()).isEmpty();
    }
}
