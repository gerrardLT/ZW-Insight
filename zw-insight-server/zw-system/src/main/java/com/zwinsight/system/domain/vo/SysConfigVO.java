package com.zwinsight.system.domain.vo;

import lombok.Data;

/**
 * 系统配置视图对象
 */
@Data
public class SysConfigVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置名称
     */
    private String configName;

    /**
     * 配置分组
     */
    private String configGroup;

    /**
     * 值类型（NUMBER/BOOLEAN/STRING/JSON）
     */
    private String valueType;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 值范围
     */
    private String valueRange;

    /**
     * 备注
     */
    private String remark;
}
