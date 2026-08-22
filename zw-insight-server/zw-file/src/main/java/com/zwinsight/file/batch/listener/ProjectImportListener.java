package com.zwinsight.file.batch.listener;

import cn.hutool.core.util.StrUtil;
import com.zwinsight.file.batch.dto.ProjectExcelDTO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 项目报备导入监听器
 * <p>
 * 通过构造器注入校验函数和保存函数，避免直接依赖业务模块 Mapper。
 * </p>
 */
@Slf4j
public class ProjectImportListener extends AbstractImportListener<ProjectExcelDTO> {

    private final Function<String, Boolean> projectNameExistsChecker;
    private final Consumer<List<ProjectExcelDTO>> batchSaveAction;

    /**
     * @param projectNameExistsChecker 检查项目名称是否已存在
     * @param batchSaveAction          批量保存动作
     */
    public ProjectImportListener(
            Function<String, Boolean> projectNameExistsChecker,
            Consumer<List<ProjectExcelDTO>> batchSaveAction) {
        this.projectNameExistsChecker = projectNameExistsChecker;
        this.batchSaveAction = batchSaveAction;
    }

    @Override
    protected String validate(ProjectExcelDTO data) {
        if (StrUtil.isBlank(data.getProjectName())) {
            return "项目名称不能为空";
        }
        if (StrUtil.isNotBlank(data.getBudgetAmount())) {
            try {
                new BigDecimal(data.getBudgetAmount().trim());
            } catch (NumberFormatException e) {
                return "预算金额格式错误";
            }
        }
        if (projectNameExistsChecker.apply(data.getProjectName().trim())) {
            return "项目名称 [" + data.getProjectName() + "] 已存在";
        }
        return null;
    }

    @Override
    protected void batchSave(List<ProjectExcelDTO> dataList) {
        batchSaveAction.accept(dataList);
        log.info("项目报备批量导入 {} 条", dataList.size());
    }
}
