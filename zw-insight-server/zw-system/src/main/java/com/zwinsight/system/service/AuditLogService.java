package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
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
                // 跨租户水平越权修复（2026-08-14）：sys_* 免拦截器过滤，
                // 显式按当前租户条件化过滤；历史 NULL 行仅在无上下文时可见
                .eq(SecurityContextHolder.getTenantId() != null,
                        SysAuditLog::getTenantId, SecurityContextHolder.getTenantId())
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
        // 补齐租户/操作人上下文（原实现未写 tenant_id，致审计日志跨租户可见
        // 且无法按租户过滤；2026-08-14 跨租户越权修复配套）
        log.setTenantId(SecurityContextHolder.getTenantId());
        log.setOperUserId(SecurityContextHolder.getUserId());
        auditLogMapper.insert(log);
    }
}
