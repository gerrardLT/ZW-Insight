package com.zwinsight.site.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.site.domain.BizScheduleFeedback;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.site.service.ScheduleFeedbackService;
import com.zwinsight.site.service.SchedulePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 进度管理接口
 */
@RestController
@RequestMapping("/api/v1/site/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final SchedulePlanService planService;
    private final ScheduleFeedbackService feedbackService;

    // ===== 进度计划 =====

    @GetMapping("/plan/{projectId}")
    public R<List<BizSchedulePlan>> planTree(@PathVariable Long projectId) {
        return R.ok(planService.list(projectId));
    }

    /** 进度计划分页（真分页 PageResult，支持 projectName/taskName 筛选） */
    @GetMapping("/page")
    public R<PageResult<BizSchedulePlan>> planPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String taskName) {
        return R.ok(planService.page(page, size, projectId, projectName, taskName));
    }

    @PostMapping("/plan")
    public R<Void> savePlan(@RequestBody BizSchedulePlan plan) {
        planService.save(plan);
        return R.ok();
    }

    @PutMapping("/plan/{id}")
    public R<Void> updatePlan(@PathVariable Long id, @RequestBody BizSchedulePlan plan) {
        plan.setId(id);
        planService.update(plan);
        return R.ok();
    }

    @DeleteMapping("/plan/{id}")
    public R<Void> deletePlan(@PathVariable Long id) {
        planService.delete(id);
        return R.ok();
    }

    // ===== 进度反馈 =====

    @GetMapping("/feedback/page")
    public R<PageResult<BizScheduleFeedback>> feedbackPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long planId) {
        return R.ok(feedbackService.page(page, size, projectId, planId));
    }

    @PostMapping("/feedback")
    public R<Void> saveFeedback(@RequestBody BizScheduleFeedback feedback) {
        feedbackService.save(feedback);
        return R.ok();
    }

    @PostMapping("/feedback/{id}/submit")
    public R<Void> submitFeedback(@PathVariable Long id) {
        feedbackService.submit(id);
        return R.ok();
    }
}
