package com.zwinsight.system.batch;

import com.zwinsight.file.batch.dto.SysUserExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.SysUserImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 系统用户批量导入导出处理器
 * TODO: 接入 SysUserMapper 完成实际持久化
 */
@Component
@RequiredArgsConstructor
public class SysUserBatchHandler implements BatchModuleHandler {

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.SYS_USER == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return SysUserExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        return new SysUserImportListener();
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        // TODO: 查询用户数据并转换为 SysUserExcelDTO
        return Collections.emptyList();
    }
}
