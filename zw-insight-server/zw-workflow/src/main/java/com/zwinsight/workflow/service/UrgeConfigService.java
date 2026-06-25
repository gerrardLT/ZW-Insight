package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfUrgeConfig;
import com.zwinsight.workflow.mapper.WfUrgeConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 催办配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrgeConfigService {

    private final WfUrgeConfigMapper urgeConfigMapper;

    /**
     * 获取当前催办配置
     */
    public WfUrgeConfig getConfig() {
        LambdaQueryWrapper<WfUrgeConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(WfUrgeConfig::getCreatedAt)
                .last("LIMIT 1");
        WfUrgeConfig config = urgeConfigMapper.selectOne(wrapper);

        if (config == null) {
            // 返回默认值
            config = new WfUrgeConfig();
            config.setTimeoutHours(24);
            config.setIntervalHours(4);
            config.setMaxUrgeCount(3);
            config.setAutoUrgeEnabled(1);
        }
        return config;
    }

    /**
     * 保存或更新催办配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(WfUrgeConfig config) {
        // 参数校验
        if (config.getTimeoutHours() != null && config.getTimeoutHours() < 1) {
            throw new BusinessException("超时时间不能小于1小时");
        }
        if (config.getIntervalHours() != null && config.getIntervalHours() < 1) {
            throw new BusinessException("催办间隔不能小于1小时");
        }
        if (config.getMaxUrgeCount() != null && config.getMaxUrgeCount() < 1) {
            throw new BusinessException("最大催办次数不能小于1次");
        }

        // 查找已有配置
        LambdaQueryWrapper<WfUrgeConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(WfUrgeConfig::getCreatedAt)
                .last("LIMIT 1");
        WfUrgeConfig existing = urgeConfigMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setTimeoutHours(config.getTimeoutHours());
            existing.setIntervalHours(config.getIntervalHours());
            existing.setMaxUrgeCount(config.getMaxUrgeCount());
            existing.setAutoUrgeEnabled(config.getAutoUrgeEnabled());
            urgeConfigMapper.updateById(existing);
            log.info("催办配置已更新, id={}", existing.getId());
        } else {
            urgeConfigMapper.insert(config);
            log.info("催办配置已新建, id={}", config.getId());
        }
    }
}
