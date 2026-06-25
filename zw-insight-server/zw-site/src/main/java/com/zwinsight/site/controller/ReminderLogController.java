package com.zwinsight.site.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.site.domain.BizReminderLog;
import com.zwinsight.site.dto.ReminderStatsVO;
import com.zwinsight.site.service.ReminderLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 整改催办日志接口
 */
@RestController
@RequestMapping("/api/v1/site")
@RequiredArgsConstructor
public class ReminderLogController {

    private final ReminderLogService reminderLogService;

    /**
     * 查询某检查记录的催办历史（按发送时间倒序）
     *
     * @param inspectionId 检查记录ID
     * @return 催办日志列表
     */
    @GetMapping("/reminder-logs/{inspectionId}")
    public R<List<BizReminderLog>> getLogsByInspectionId(@PathVariable Long inspectionId) {
        List<BizReminderLog> logs = reminderLogService.getLogsByInspectionId(inspectionId);
        return R.ok(logs);
    }

    /**
     * 查询项目下的催办统计数据
     *
     * @param projectId 项目ID
     * @return 催办统计（超期整改总数、已催办次数、已升级通知次数）
     */
    @GetMapping("/reminder-stats/{projectId}")
    public R<ReminderStatsVO> getStatsByProjectId(@PathVariable Long projectId) {
        ReminderStatsVO stats = reminderLogService.getStatsByProjectId(projectId);
        return R.ok(stats);
    }
}
