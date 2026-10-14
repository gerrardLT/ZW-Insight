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

    /**
     * 监听业务模块派发的字段变更事件并落库。
     * 同步执行于发布方线程；留痕失败只记日志，不得回滚或阻断业务更新。
     */
    @org.springframework.context.event.EventListener
    public void onAuditLogEvent(com.zwinsight.common.event.AuditLogRecordEvent event) {
        try {
            SysAuditLog row = new SysAuditLog();
            row.setTableName(event.getTableName());
            row.setRecordId(event.getRecordId());
            row.setFieldName(event.getFieldName());
            row.setOldValue(truncate(event.getOldValue()));
            row.setNewValue(truncate(event.getNewValue()));
            row.setOperTime(LocalDateTime.now());
            row.setTenantId(event.getTenantId());
            row.setOperUserId(event.getOperUserId());
            row.setOperUserName(event.getOperUserName());
            auditLogMapper.insert(row);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditLogService.class)
                    .error("字段变更留痕落库失败: table={}, recordId={}, field={}",
                            event.getTableName(), event.getRecordId(), event.getFieldName(), e);
        }
    }

    /** 列宽 VARCHAR(2000)，超长截断避免整条留痕写入失败 */
    private static String truncate(String v) {
        return v != null && v.length() > 2000 ? v.substring(0, 2000) : v;
    }
}
