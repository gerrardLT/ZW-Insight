package com.zwinsight.project.batch;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.file.batch.dto.ProjectExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.ProjectImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 项目报备批量导入导出处理器
 */
@Component
@RequiredArgsConstructor
public class ProjectBatchHandler implements BatchModuleHandler {

    private final BizProjectMapper projectMapper;
    private final ProjectService projectService;

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.PROJECT == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return ProjectExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        return new ProjectImportListener(
                // 唯一性校验：检查项目名称是否存在
                projectName -> {
                    Long count = projectMapper.selectCount(
                            new LambdaQueryWrapper<BizProject>()
                                    .eq(BizProject::getProjectName, projectName)
                    );
                    return count != null && count > 0;
                },
                // 批量保存（走 ProjectService.save：自动生成编号/状态/金额初始化）
                dataList -> {
                    for (ProjectExcelDTO dto : dataList) {
                        BizProject entity = new BizProject();
                        entity.setProjectName(dto.getProjectName().trim());
                        entity.setProjectNature(StrUtil.trimToNull(dto.getProjectNature()));
                        entity.setProjectType(StrUtil.trimToNull(dto.getProjectType()));
                        entity.setOwnerCompanyName(StrUtil.trimToNull(dto.getOwnerCompanyName()));
                        entity.setProjectAddress(StrUtil.trimToNull(dto.getProjectAddress()));
                        entity.setContactName(StrUtil.trimToNull(dto.getContactName()));
                        entity.setContactPhone(StrUtil.trimToNull(dto.getContactPhone()));
                        if (StrUtil.isNotBlank(dto.getBudgetAmount())) {
                            entity.setBudgetAmount(new BigDecimal(dto.getBudgetAmount().trim()));
                        }
                        projectService.save(entity);
                    }
                }
        );
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        List<BizProject> list = projectMapper.selectList(null);
        return list.stream().map(entity -> {
            ProjectExcelDTO dto = new ProjectExcelDTO();
            dto.setProjectName(entity.getProjectName());
            dto.setProjectNature(entity.getProjectNature());
            dto.setProjectType(entity.getProjectType());
            dto.setOwnerCompanyName(entity.getOwnerCompanyName());
            dto.setProjectAddress(entity.getProjectAddress());
            dto.setContactName(entity.getContactName());
            dto.setContactPhone(entity.getContactPhone());
            dto.setBudgetAmount(entity.getBudgetAmount() != null ? entity.getBudgetAmount().toPlainString() : "");
            return dto;
        }).toList();
    }
}
