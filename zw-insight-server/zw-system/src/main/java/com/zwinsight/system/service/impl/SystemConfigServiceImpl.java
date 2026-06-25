package com.zwinsight.system.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.system.domain.SysConfig;
import com.zwinsight.system.domain.SysConfigChangeLog;
import com.zwinsight.system.domain.dto.ConfigUpdateRequest;
import com.zwinsight.system.domain.vo.SysConfigVO;
import com.zwinsight.system.mapper.SysConfigChangeLogMapper;
import com.zwinsight.system.mapper.SysConfigMapper;
import com.zwinsight.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final String CACHE_KEY_PREFIX = "sys:config:";
    private static final long CACHE_TTL_SECONDS = 3600; // 1小时

    private final SysConfigMapper configMapper;
    private final SysConfigChangeLogMapper changeLogMapper;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    @Override
    public List<SysConfigVO> listByGroup(String group) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigGroup, group);
        List<SysConfig> configs = configMapper.selectList(wrapper);
        return configs.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(String configKey, String configValue) {
        SysConfig config = getConfigByKey(configKey);

        // 值范围校验
        validateConfigValue(config, configValue);

        String oldValue = config.getConfigValue();
        config.setConfigValue(configValue);
        configMapper.updateById(config);

        // 清除 Redis 缓存
        evictCache(configKey);

        // 记录变更日志
        recordChangeLog(configKey, oldValue, configValue);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(List<ConfigUpdateRequest> requests) {
        for (ConfigUpdateRequest request : requests) {
            updateConfig(request.getConfigKey(), request.getConfigValue());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetToDefault(String configKey) {
        SysConfig config = getConfigByKey(configKey);

        String oldValue = config.getConfigValue();
        String defaultValue = config.getDefaultValue();

        config.setConfigValue(defaultValue);
        configMapper.updateById(config);

        // 清除 Redis 缓存
        evictCache(configKey);

        // 记录变更日志
        recordChangeLog(configKey, oldValue, defaultValue);
    }

    @Override
    public String getConfigValue(String configKey) {
        // 先从 Redis 缓存读取
        String cacheKey = CACHE_KEY_PREFIX + configKey;
        Object cachedValue = redisUtils.get(cacheKey);
        if (cachedValue != null) {
            return cachedValue.toString();
        }

        // 缓存未命中，从数据库读取
        SysConfig config = getConfigByKey(configKey);
        String value = config.getConfigValue();

        // 写入 Redis 缓存
        redisUtils.set(cacheKey, value, CACHE_TTL_SECONDS);

        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String configKey, Class<T> type) {
        String value = getConfigValue(configKey);
        if (value == null) {
            return null;
        }

        try {
            if (type == String.class) {
                return (T) value;
            } else if (type == Integer.class) {
                return (T) Integer.valueOf(value);
            } else if (type == Long.class) {
                return (T) Long.valueOf(value);
            } else if (type == Boolean.class) {
                return (T) Boolean.valueOf(value);
            } else if (type == Double.class) {
                return (T) Double.valueOf(value);
            } else {
                // JSON 反序列化
                return objectMapper.readValue(value, type);
            }
        } catch (Exception e) {
            throw new BusinessException("配置值类型转换失败: configKey=" + configKey + ", targetType=" + type.getSimpleName());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 根据 configKey 获取配置，不存在则抛异常
     */
    private SysConfig getConfigByKey(String configKey) {
        SysConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, configKey));
        if (config == null) {
            throw new BusinessException("配置项不存在: " + configKey);
        }
        return config;
    }

    /**
     * 参数值范围校验
     */
    private void validateConfigValue(SysConfig config, String newValue) {
        String valueType = config.getValueType();
        String valueRange = config.getValueRange();

        if (StrUtil.isBlank(valueType)) {
            return;
        }

        switch (valueType.toUpperCase()) {
            case "NUMBER":
                validateNumberValue(config.getConfigName(), newValue, valueRange);
                break;
            case "BOOLEAN":
                validateBooleanValue(config.getConfigName(), newValue);
                break;
            case "JSON":
                validateJsonValue(config.getConfigName(), newValue);
                break;
            case "STRING":
                // STRING 类型不做范围校验
                break;
            default:
                break;
        }
    }

    /**
     * NUMBER 类型校验：解析 "min-max" 格式并校验范围
     */
    private void validateNumberValue(String configName, String value, String valueRange) {
        // 校验是否为合法数字
        long numValue;
        try {
            numValue = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessException("参数「" + configName + "」的值必须为数字");
        }

        // 如果定义了值范围，校验是否在允许范围内
        if (StrUtil.isNotBlank(valueRange)) {
            String[] parts = valueRange.split("-");
            if (parts.length == 2) {
                try {
                    long min = Long.parseLong(parts[0].trim());
                    long max = Long.parseLong(parts[1].trim());
                    if (numValue < min || numValue > max) {
                        throw new BusinessException("参数「" + configName + "」的值必须在 " + min + " - " + max + " 范围内");
                    }
                } catch (NumberFormatException e) {
                    log.warn("配置项 {} 的 value_range 格式不正确: {}", configName, valueRange);
                }
            }
        }
    }

    /**
     * BOOLEAN 类型校验
     */
    private void validateBooleanValue(String configName, String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new BusinessException("参数「" + configName + "」的值必须为 true 或 false");
        }
    }

    /**
     * JSON 类型校验：验证是否为合法 JSON
     */
    private void validateJsonValue(String configName, String value) {
        try {
            objectMapper.readTree(value);
        } catch (Exception e) {
            throw new BusinessException("参数「" + configName + "」的值必须为合法的 JSON 格式");
        }
    }

    /**
     * 清除 Redis 缓存
     */
    private void evictCache(String configKey) {
        String cacheKey = CACHE_KEY_PREFIX + configKey;
        redisUtils.delete(cacheKey);
    }

    /**
     * 记录变更日志
     */
    private void recordChangeLog(String configKey, String oldValue, String newValue) {
        SysConfigChangeLog changeLog = new SysConfigChangeLog();
        changeLog.setConfigKey(configKey);
        changeLog.setOldValue(oldValue);
        changeLog.setNewValue(newValue);
        changeLog.setOperatorId(SecurityContextHolder.getUserId());
        changeLog.setCreatedAt(LocalDateTime.now());
        changeLogMapper.insert(changeLog);
    }

    /**
     * 实体转 VO
     */
    private SysConfigVO toVO(SysConfig config) {
        SysConfigVO vo = new SysConfigVO();
        vo.setId(config.getId());
        vo.setConfigKey(config.getConfigKey());
        vo.setConfigValue(config.getConfigValue());
        vo.setConfigName(config.getConfigName());
        vo.setConfigGroup(config.getConfigGroup());
        vo.setValueType(config.getValueType());
        vo.setDefaultValue(config.getDefaultValue());
        vo.setValueRange(config.getValueRange());
        vo.setRemark(config.getRemark());
        return vo;
    }
}
