package com.zwinsight.workflow.dto;

import lombok.Data;

/**
 * 冲突确认请求
 */
@Data
public class ConflictConfirmRequest {

    /**
     * 处理方式描述
     */
    private String resolution;
}
