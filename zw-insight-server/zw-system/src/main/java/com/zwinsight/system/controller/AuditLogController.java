package com.zwinsight.system.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.SysAuditLog;
import com.zwinsight.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志接口
 */
@RestController
@RequestMapping("/api/v1/system/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 分页查询审计日志
     */
    @GetMapping
    public R<PageResult<SysAuditLog>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) Long recordId) {
        return R.ok(auditLogService.page(page, size, tableName, recordId));
    }
}
