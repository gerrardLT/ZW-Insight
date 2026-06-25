package com.zwinsight.labor.batch;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.file.batch.dto.LaborRosterExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.LaborRosterImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 劳务花名册批量导入导出处理器
 */
@Component
@RequiredArgsConstructor
public class LaborRosterBatchHandler implements BatchModuleHandler {

    private final BizLaborRosterMapper laborRosterMapper;

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.LABOR_ROSTER == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return LaborRosterExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        return new LaborRosterImportListener(
                // 唯一性校验：检查身份证号是否存在
                idCard -> {
                    Long count = laborRosterMapper.selectCount(
                            new LambdaQueryWrapper<BizLaborRoster>()
                                    .eq(BizLaborRoster::getIdCard, idCard)
                    );
                    return count != null && count > 0;
                },
                // 批量保存
                dataList -> {
                    for (LaborRosterExcelDTO dto : dataList) {
                        BizLaborRoster entity = new BizLaborRoster();
                        entity.setWorkerName(dto.getWorkerName().trim());
                        entity.setIdCard(dto.getIdCard().trim());
                        entity.setPhone(StrUtil.trimToNull(dto.getPhone()));
                        entity.setWorkerType(StrUtil.trimToNull(dto.getWorkerType()));
                        entity.setProjectId(projectId);
                        entity.setStatus(1); // 在岗

                        laborRosterMapper.insert(entity);
                    }
                }
        );
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        List<BizLaborRoster> list = laborRosterMapper.selectList(null);
        return list.stream().map(entity -> {
            LaborRosterExcelDTO dto = new LaborRosterExcelDTO();
            dto.setWorkerName(entity.getWorkerName());
            dto.setIdCard(entity.getIdCard());
            dto.setPhone(entity.getPhone());
            dto.setWorkerType(entity.getWorkerType());
            return dto;
        }).toList();
    }
}
