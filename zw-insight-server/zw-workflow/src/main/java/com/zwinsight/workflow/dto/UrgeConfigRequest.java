package com.zwinsight.workflow.dto;

import lombok.Data;

/**
 * 催办配置请求
 */
@Data
public class UrgeConfigRequest {

    /**
     * 超时时间（小时）
     */
    private Integer timeoutHours;

    /**
     * 催办间隔（小时）
     */
    private Integer intervalHours;

    /**
     * 最大催办次数
     */
    private Integer maxUrgeCount;

    /**
     * 是否启用自动催办（0-禁用 1-启用）
     */
    private Integer autoUrgeEnabled;
}
