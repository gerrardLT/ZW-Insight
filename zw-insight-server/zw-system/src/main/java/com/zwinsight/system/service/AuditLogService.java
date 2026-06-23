package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.system.domain.SysAuditLog;
import com.zwinsight.system.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计日志服务
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final SysAuditLogMapper auditLogMapper;

    /**
     * 分页查询审计日志
     */
    public PageResult<SysAuditLog> page(int page, int size, String tableName, Long recordId) {
        Page<SysAuditLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tableName != null, SysAuditLog::getTableName, tableName)
                .eq(recordId != null, SysAuditLog::getRecordId, recordId)
                .orderByDesc(SysAuditLog::getOperTime);
        Page<SysAuditLog> result = auditLogMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 记录审计日志
     */
    public void save(String tableName, Long recordId, String fieldName,
                     String oldValue, String newValue) {
        SysAuditLog log = new SysAuditLog();
        log.setTableName(tableName);
        log.setRecordId(recordId);
        log.setFieldName(fieldName);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setOperTime(LocalDateTime.now());
        // operUserId 和 operUserName 由 SecurityContext 获取，此处可从 ThreadLocal 中获取
        auditLogMapper.insert(log);
    }
}
