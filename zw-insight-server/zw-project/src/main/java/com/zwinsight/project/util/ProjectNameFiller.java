package com.zwinsight.project.util;

import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目名称回填工具
 * <p>
 * 业务列表实体通常仅持久化 projectId，而前端列表需要展示项目名称（projectName）。
 * 本工具通过一次批量查询 biz_project 构建 id→name 映射后统一回填，避免逐条查询（N+1）。
 * projectName 字段在业务实体中以 {@code @TableField(exist = false)} 声明，不参与持久化。
 * </p>
 */
public final class ProjectNameFiller {

    private ProjectNameFiller() {
    }

    /**
     * 批量回填 projectName。
     *
     * @param records        列表记录
     * @param projectMapper  项目 Mapper
     * @param getProjectId   记录 → projectId 提取函数
     * @param setProjectName 记录 + projectName 赋值函数
     * @param <T>            记录类型
     */
    public static <T> void fill(List<T> records,
                                BizProjectMapper projectMapper,
                                Function<T, Long> getProjectId,
                                BiConsumer<T, String> setProjectName) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> projectIds = records.stream()
                .map(getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (projectIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = projectMapper.selectBatchIds(projectIds).stream()
                .filter(p -> p.getProjectName() != null)
                .collect(Collectors.toMap(BizProject::getId, BizProject::getProjectName, (a, b) -> a));
        for (T record : records) {
            Long projectId = getProjectId.apply(record);
            if (projectId != null) {
                setProjectName.accept(record, nameMap.get(projectId));
            }
        }
    }
}
