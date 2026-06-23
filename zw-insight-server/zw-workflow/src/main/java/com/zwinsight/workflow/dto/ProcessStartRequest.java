package com.zwinsight.workflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * 发起流程请求
 */
@Data
public class ProcessStartRequest {

    /**
     * 业务类型标识
     */
    private String businessType;

    /**
     * 业务ID
     */
    private Long businessId;

    /**
     * 流程标识
     */
    private String processKey;

    /**
     * 流程变量
     */
    private Map<String, Object> variables;
}
