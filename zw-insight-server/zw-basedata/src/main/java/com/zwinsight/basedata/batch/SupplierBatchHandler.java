package com.zwinsight.basedata.batch;

import com.zwinsight.file.batch.dto.SupplierExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.SupplierImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 供应商批量导入导出处理器
 * TODO: 接入 BdSupplierMapper 完成实际持久化
 */
@Component
@RequiredArgsConstructor
public class SupplierBatchHandler implements BatchModuleHandler {

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.SUPPLIER == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return SupplierExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        return new SupplierImportListener();
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        // TODO: 查询供应商数据并转换为 SupplierExcelDTO
        return Collections.emptyList();
    }
}
