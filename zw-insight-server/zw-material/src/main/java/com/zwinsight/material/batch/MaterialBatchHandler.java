package com.zwinsight.material.batch;

import com.zwinsight.file.batch.dto.MaterialExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.MaterialImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 材料字典批量导入导出处理器
 * TODO: 接入 BdMaterialMapper 完成实际持久化
 */
@Component
@RequiredArgsConstructor
public class MaterialBatchHandler implements BatchModuleHandler {

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.MATERIAL == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return MaterialExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        return new MaterialImportListener();
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        // TODO: 查询材料字典数据并转换为 MaterialExcelDTO
        return Collections.emptyList();
    }
}
