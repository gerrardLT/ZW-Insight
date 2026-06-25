package com.zwinsight.system.domain.dto;

import lombok.Data;

/**
 * 配置更新请求
 */
@Data
public class ConfigUpdateRequest {

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;
}
