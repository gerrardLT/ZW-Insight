package com.zwinsight.system.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.SysLoginLog;
import com.zwinsight.system.domain.SysOperLog;
import com.zwinsight.system.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日志管理接口
 */
@RestController
@RequestMapping("/api/v1/system/log")
@RequiredArgsConstructor
public class SysLogController {

    private final SysLogService logService;

    @GetMapping("/oper")
    public R<PageResult<SysOperLog>> pageOperLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operType) {
        return R.ok(logService.pageOperLogs(page, size, module, operType));
    }

    @GetMapping("/login")
    public R<PageResult<SysLoginLog>> pageLoginLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String loginName) {
        return R.ok(logService.pageLoginLogs(page, size, loginName));
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
