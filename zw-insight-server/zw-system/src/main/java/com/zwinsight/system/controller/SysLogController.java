package com.zwinsight.system.controller;

import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.system.domain.SysLoginLog;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.system.domain.SysOperLog;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.system.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 日志管理接口
 */
@RestController
@RequestMapping("/api/v1/system/log")
@RequiredArgsConstructor
@RequiresPermission("system:view")
public class SysLogController {

    private final SysLogService logService;

    /** 2026-10-10：补 operator/startTime/endTime——前端「操作人 + 时间范围」筛选此前随请求下发但无入参，筛选静默失效。 */
    @GetMapping("/oper")
    public R<PageResult<SysOperLog>> pageOperLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operType,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endTime) {
        return R.ok(logService.pageOperLogs(page, size, module, operType, operator, startTime, endTime));
    }

    @GetMapping("/login")
    public R<PageResult<SysLoginLog>> pageLoginLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String loginName,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endTime) {
        return R.ok(logService.pageLoginLogs(page, size, loginName, operator, startTime, endTime));
    }

    @DeleteMapping("/oper")
    public R<Void> deleteOperLogs(@RequestBody List<Long> ids) {
        logService.deleteOperLogs(ids);
        return R.ok();
    }

    @DeleteMapping("/login")
    public R<Void> deleteLoginLogs(@RequestBody List<Long> ids) {
        logService.deleteLoginLogs(ids);
        return R.ok();
    }
}
