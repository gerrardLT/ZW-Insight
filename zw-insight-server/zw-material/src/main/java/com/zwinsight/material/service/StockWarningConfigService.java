package com.zwinsight.material.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizStockWarningConfig;
import com.zwinsight.material.mapper.BizStockWarningConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 库存安全阈值配置服务
 * <p>
 * 按 projectId+materialId 维度维护安全库存（safetyStock），projectId=NULL 为全局默认配置。
 * save 采用 upsert 语义：同一 projectId+materialId 已存在时更新，避免重复配置。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class StockWarningConfigService {

    private final BizStockWarningConfigMapper configMapper;

    /**
     * 分页查询配置（支持按材料名称模糊筛选）
     */
    public PageResult<BizStockWarningConfig> page(int page, int size, String materialName) {
        Page<BizStockWarningConfig> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizStockWarningConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(materialName), BizStockWarningConfig::getMaterialName, materialName)
                .orderByDesc(BizStockWarningConfig::getCreatedAt);
        return PageResult.of(configMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 查询全部启用的配置（供库存页预警展示参考，不分页）
     */
    public List<BizStockWarningConfig> listEnabled() {
        LambdaQueryWrapper<BizStockWarningConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizStockWarningConfig::getEnabled, 1);
        return configMapper.selectList(wrapper);
    }

    /**
     * 新增或更新配置（upsert：projectId+materialId 已存在则更新）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizStockWarningConfig config) {
        if (config.getMaterialId() == null) {
            throw new BusinessException("请选择材料");
        }
        if (config.getSafetyStock() == null || config.getSafetyStock().signum() < 0) {
            throw new BusinessException("安全库存必须为非负数");
        }
        if (config.getEnabled() == null) {
            config.setEnabled(1);
        }

        // projectId 为 null 表示全局默认配置
        LambdaQueryWrapper<BizStockWarningConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizStockWarningConfig::getMaterialId, config.getMaterialId());
        if (config.getProjectId() != null) {
            wrapper.eq(BizStockWarningConfig::getProjectId, config.getProjectId());
        } else {
            wrapper.isNull(BizStockWarningConfig::getProjectId);
        }
        BizStockWarningConfig existing = configMapper.selectOne(wrapper);
        if (existing != null) {
            existing.setSafetyStock(config.getSafetyStock());
            existing.setEnabled(config.getEnabled());
            if (StrUtil.isNotBlank(config.getMaterialName())) {
                existing.setMaterialName(config.getMaterialName());
            }
            configMapper.updateById(existing);
            config.setId(existing.getId());
            return;
        }
        configMapper.insert(config);
    }

    /**
     * 删除配置
     */
    public void delete(Long id) {
        BizStockWarningConfig existing = configMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("预警配置不存在");
        }
        configMapper.deleteById(id);
    }
}
