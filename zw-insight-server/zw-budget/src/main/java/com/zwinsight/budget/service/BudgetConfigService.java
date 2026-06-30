package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizBudgetConfig;
import com.zwinsight.budget.mapper.BizBudgetConfigMapper;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 预算管控配置服务
 */
@Service
@RequiredArgsConstructor
public class BudgetConfigService {

    private final BizBudgetConfigMapper budgetConfigMapper;

    /**
     * 获取配置（优先项目级，其次全局）
     */
    public BizBudgetConfig getConfig(Long projectId) {
        // 先查项目级配置
        if (projectId != null) {
            LambdaQueryWrapper<BizBudgetConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizBudgetConfig::getProjectId, projectId);
            BizBudgetConfig config = budgetConfigMapper.selectOne(wrapper);
            if (config != null) {
                return config;
            }
        }

        // 再查全局配置
        LambdaQueryWrapper<BizBudgetConfig> globalWrapper = new LambdaQueryWrapper<>();
        globalWrapper.isNull(BizBudgetConfig::getProjectId);
        return budgetConfigMapper.selectOne(globalWrapper);
    }

    /**
     * 保存配置
     */
    public void save(BizBudgetConfig config) {
        budgetConfigMapper.insert(config);
    }

    /**
     * 更新配置
     */
    public void update(BizBudgetConfig config) {
        BizBudgetConfig existing = budgetConfigMapper.selectById(config.getId());
        if (existing == null) {
            throw new BusinessException("配置不存在");
        }
        budgetConfigMapper.updateById(config);
    }

    /**
     * 删除配置
     */
    public void delete(Long id) {
        budgetConfigMapper.deleteById(id);
    }
}
