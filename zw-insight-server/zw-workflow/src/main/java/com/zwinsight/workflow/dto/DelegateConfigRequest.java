package com.zwinsight.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建委托配置请求
 */
@Data
public class DelegateConfigRequest {

    /**
     * 代理人用户ID
     */
    private Long delegateId;

    /**
     * 委托开始时间
     */
    private LocalDateTime startTime;

    /**
     * 委托结束时间
     */
    private LocalDateTime endTime;

    /**
     * 委托原因/备注
     */
    private String reason;
}
