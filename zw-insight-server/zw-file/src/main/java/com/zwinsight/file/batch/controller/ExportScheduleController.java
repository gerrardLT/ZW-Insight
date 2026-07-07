package com.zwinsight.file.batch.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.file.batch.domain.BizExportSchedule;
import com.zwinsight.file.batch.service.ExportScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据导出中心接口
 * <p>
 * 提供定时导出配置的 CRUD、手动触发执行、可用模块列表查询。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/export-schedule")
@RequiredArgsConstructor
public class ExportScheduleController {

    private final ExportScheduleService exportScheduleService;

    /** 分页查询定时导出配置 */
    @GetMapping("/page")
    public R<PageResult<BizExportSchedule>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(exportScheduleService.page(page, size));
    }

    /** 创建定时导出配置 */
    @PostMapping
    public R<Void> save(@RequestBody BizExportSchedule schedule) {
        exportScheduleService.save(schedule);
        return R.ok();
    }

    /** 更新定时导出配置 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizExportSchedule schedule) {
        schedule.setId(id);
        exportScheduleService.update(schedule);
        return R.ok();
    }

    /** 删除定时导出配置 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        exportScheduleService.delete(id);
        return R.ok();
    }

    /** 手动立即执行某个导出任务 */
    @PostMapping("/{id}/execute")
    public R<Long> executeNow(@PathVariable Long id) {
        Long taskId = exportScheduleService.executeNow(id);
        return R.ok("导出任务已触发", taskId);
    }

    /** 获取所有可导出模块列表 */
    @GetMapping("/modules")
    public R<List<Map<String, String>>> getAvailableModules() {
        return R.ok(exportScheduleService.getAvailableModules());
    }
}
