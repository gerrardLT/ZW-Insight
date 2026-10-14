package com.zwinsight.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 字段审计日志事件
 * <p>
 * 遵循 Published Language 模式放于 zw-common，解耦业务模块与 zw-system 的审计落库服务。
 * </p>
 */
@Getter
public class AuditLogRecordEvent extends ApplicationEvent {

    private final String tableName;
    private final Long recordId;
    private final String fieldName;
    private final String oldValue;
    private final String newValue;
    private final Long tenantId;
    private final Long operUserId;
    private final String operUserName;

    public AuditLogRecordEvent(Object source, String tableName, Long recordId,
                               String fieldName, String oldValue, String newValue,
                               Long tenantId, Long operUserId, String operUserName) {
        super(source);
        this.tableName = tableName;
        this.recordId = recordId;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.tenantId = tenantId;
        this.operUserId = operUserId;
        this.operUserName = operUserName;
    }
}
