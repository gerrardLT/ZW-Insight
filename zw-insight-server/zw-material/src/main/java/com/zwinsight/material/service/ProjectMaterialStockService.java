package com.zwinsight.material.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.domain.BizStockWarningConfig;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.material.mapper.BizStockWarningConfigMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目材料库存查询服务
 */
@Service
@RequiredArgsConstructor
public class ProjectMaterialStockService {

    private final BizProjectMaterialStockMapper stockMapper;
    private final BizStockWarningConfigMapper warningConfigMapper;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询库存（支持材料名称/项目名称/库存预警筛选，回填项目名与最低库存）
     *
     * @param warning NORMAL=库存正常 / LOW=库存不足（低于安全库存）
     */
    public PageResult<BizProjectMaterialStock> page(int page, int size, Long projectId,
                                                    String materialName, String projectName, String warning) {
        // projectName 不是本表字段，需先经 biz_project 解析为 projectId 集合再过滤
        List<Long> nameMatchedIds = null;
        if (StrUtil.isNotBlank(projectName)) {
            LambdaQueryWrapper<BizProject> projectWrapper = new LambdaQueryWrapper<>();
            projectWrapper.like(BizProject::getProjectName, projectName);
            nameMatchedIds = projectMapper.selectList(projectWrapper).stream()
                    .map(BizProject::getId).collect(Collectors.toList());
            if (nameMatchedIds.isEmpty()) {
                return PageResult.of(new Page<>(page, size));
            }
        }

        LambdaQueryWrapper<BizProjectMaterialStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizProjectMaterialStock::getProjectId, projectId)
                .in(nameMatchedIds != null, BizProjectMaterialStock::getProjectId, nameMatchedIds)
                .like(StrUtil.isNotBlank(materialName), BizProjectMaterialStock::getMaterialName, materialName)
                .orderByAsc(BizProjectMaterialStock::getMaterialName);

        // 预警筛选需与安全库存(safetyStock)逐行比较，无法在单表 SQL 表达，故取全量匹配集在内存中过滤后手动分页
        if (StrUtil.isNotBlank(warning)) {
            List<BizProjectMaterialStock> all = stockMapper.selectList(wrapper);
            fillMinStock(all);
            boolean wantLow = "LOW".equalsIgnoreCase(warning);
            List<BizProjectMaterialStock> filtered = all.stream()
                    .filter(s -> isLowStock(s) == wantLow)
                    .collect(Collectors.toList());
            long total = filtered.size();
            int fromIndex = Math.max(0, (page - 1) * size);
            int toIndex = Math.min(filtered.size(), fromIndex + size);
            List<BizProjectMaterialStock> pageRecords = fromIndex >= filtered.size()
                    ? Collections.emptyList() : filtered.subList(fromIndex, toIndex);
            ProjectNameFiller.fill(pageRecords, projectMapper,
                    BizProjectMaterialStock::getProjectId, BizProjectMaterialStock::setProjectName);
            Page<BizProjectMaterialStock> manual = new Page<>(page, size, total);
            manual.setRecords(pageRecords);
            return PageResult.of(manual);
        }

        Page<BizProjectMaterialStock> pageParam = new Page<>(page, size);
        Page<BizProjectMaterialStock> result = stockMapper.selectPage(pageParam, wrapper);
        List<BizProjectMaterialStock> records = result.getRecords();
        fillMinStock(records);
        ProjectNameFiller.fill(records, projectMapper,
                BizProjectMaterialStock::getProjectId, BizProjectMaterialStock::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 批量回填最低库存(safetyStock)：优先取项目+材料专属配置，回退到全局(projectId=NULL)配置
     */
    private void fillMinStock(List<BizProjectMaterialStock> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> materialIds = records.stream()
                .map(BizProjectMaterialStock::getMaterialId)
                .filter(java.util.Objects::nonNull)
                .distinct().collect(Collectors.toList());
        if (materialIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<BizStockWarningConfig> cfgWrapper = new LambdaQueryWrapper<>();
        cfgWrapper.eq(BizStockWarningConfig::getEnabled, 1)
                .in(BizStockWarningConfig::getMaterialId, materialIds);
        List<BizStockWarningConfig> configs = warningConfigMapper.selectList(cfgWrapper);
        // key: materialId → (projectId → safetyStock)，projectId 用 -1 代表全局
        Map<Long, Map<Long, BigDecimal>> configMap = configs.stream().collect(Collectors.groupingBy(
                BizStockWarningConfig::getMaterialId,
                Collectors.toMap(c -> c.getProjectId() == null ? -1L : c.getProjectId(),
                        BizStockWarningConfig::getSafetyStock, (a, b) -> a)));
        for (BizProjectMaterialStock stock : records) {
            Map<Long, BigDecimal> byProject = configMap.get(stock.getMaterialId());
            if (byProject == null) {
                continue;
            }
            BigDecimal safety = byProject.get(stock.getProjectId());
            if (safety == null) {
                safety = byProject.get(-1L); // 全局默认
            }
            stock.setMinStock(safety);
        }
    }

    /** 是否库存不足：已配置安全库存且当前库存不高于安全库存 */
    private boolean isLowStock(BizProjectMaterialStock stock) {
        return stock.getMinStock() != null
                && stock.getStockQuantity() != null
                && stock.getStockQuantity().compareTo(stock.getMinStock()) <= 0;
    }

    /**
     * 按项目查询库存列表（只读）
     */
    public List<BizProjectMaterialStock> getByProject(Long projectId) {
        LambdaQueryWrapper<BizProjectMaterialStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMaterialStock::getProjectId, projectId)
                .orderByAsc(BizProjectMaterialStock::getMaterialName);
        List<BizProjectMaterialStock> records = stockMapper.selectList(wrapper);
        fillMinStock(records);
        return records;
    }
}
