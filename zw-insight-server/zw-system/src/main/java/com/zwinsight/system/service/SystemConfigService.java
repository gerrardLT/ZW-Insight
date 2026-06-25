package com.zwinsight.system.service;

import com.zwinsight.system.domain.dto.ConfigUpdateRequest;
import com.zwinsight.system.domain.vo.SysConfigVO;

import java.util.List;

/**
 * 系统配置服务接口
 */
public interface SystemConfigService {

    /**
     * 按分组查询配置列表
     */
    List<SysConfigVO> listByGroup(String group);

    /**
     * 更新配置值（含校验）
     */
    void updateConfig(String configKey, String configValue);

    /**
     * 批量更新配置
     */
    void batchUpdate(List<ConfigUpdateRequest> requests);

    /**
     * 恢复默认值
     */
    void resetToDefault(String configKey);

    /**
     * 获取配置值（带 Redis 缓存）
     */
    String getConfigValue(String configKey);

    /**
     * 获取配置值并转换类型
     */
    <T> T getConfigValue(String configKey, Class<T> type);
}
