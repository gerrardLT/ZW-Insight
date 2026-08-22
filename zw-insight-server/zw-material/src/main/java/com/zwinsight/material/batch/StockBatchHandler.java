package com.zwinsight.material.batch;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.batch.dto.StockExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.service.ProjectMaterialStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 库存查询导出处理器（仅导出，不支持导入）
 * <p>
 * 复用 {@link ProjectMaterialStockService#listForExport} 的安全库存回填与预警过滤逻辑，
 * 导出列含库存状态标记（与库存查询页预警口径一致）。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class StockBatchHandler implements BatchModuleHandler {

    private final ProjectMaterialStockService stockService;

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.STOCK == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return StockExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        throw new BusinessException("库存查询模块不支持批量导入");
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        Long projectId = paramLong(params, "projectId");
        String materialName = paramString(params, "materialName");
        String projectName = paramString(params, "projectName");
        String warning = paramString(params, "warning");
        List<BizProjectMaterialStock> list = stockService.listForExport(projectId, materialName, projectName, warning);
        return list.stream().map(stock -> {
            StockExcelDTO dto = new StockExcelDTO();
            dto.setMaterialName(stock.getMaterialName());
            dto.setSpecification(stock.getSpecification());
            dto.setUnit(stock.getUnit());
            dto.setStockQuantity(stock.getStockQuantity() != null ? stock.getStockQuantity().toPlainString() : "");
            dto.setMinStock(stock.getMinStock() != null ? stock.getMinStock().toPlainString() : "");
            dto.setProjectName(stock.getProjectName());
            dto.setWarningStatus(isLowStock(stock) ? "库存不足" : "正常");
            return dto;
        }).toList();
    }

    /** 与 ProjectMaterialStockService.isLowStock 同口径：已配置安全库存且当前库存不高于安全库存 */
    private boolean isLowStock(BizProjectMaterialStock stock) {
        return stock.getMinStock() != null
                && stock.getStockQuantity() != null
                && stock.getStockQuantity().compareTo(stock.getMinStock()) <= 0;
    }

    private Long paramLong(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Long.valueOf(value.toString().trim());
    }

    private String paramString(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        return value == null ? null : value.toString();
    }
}
