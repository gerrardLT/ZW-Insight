package com.zwinsight.machine.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.machine.domain.BizMachineWorkLog;
import com.zwinsight.machine.service.MachineWorkLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 机械工作日志接口
 */
@RestController
@RequestMapping("/api/v1/machine/work-log")
@RequiredArgsConstructor
public class MachineWorkLogController {

    private final MachineWorkLogService workLogService;

    @GetMapping("/page")
    public R<PageResult<BizMachineWorkLog>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) Long projectId) {
        return R.ok(workLogService.page(page, size, machineId, projectId));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizMachineWorkLog workLog) {
        workLogService.save(workLog);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMachineWorkLog workLog) {
        workLog.setId(id);
        workLogService.update(workLog);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        workLogService.delete(id);
        return R.ok();
    }
}
