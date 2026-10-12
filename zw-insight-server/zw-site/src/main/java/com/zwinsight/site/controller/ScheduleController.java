package com.zwinsight.site.controller;

import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.site.domain.BizScheduleFeedback;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.site.domain.BizSchedulePlan;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.site.service.ScheduleFeedbackService;
import com.zwinsight.common.security.RequiresPermission;
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
@RequiresPermission("site:view")
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

    /**
     * 滞后任务清单（2026-10-12 新增）。
     * 判定式：计划结束日期早于今天且任务未完成；供看板/风险扫描等监督链路消费。
     */
    @GetMapping("/delayed")
    public R<List<BizSchedulePlan>> delayedPlans() {
        return R.ok(planService.listDelayed());
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

    /**
     * 提交进度反馈并立即生效（创建 + 回写计划，一步完成；2026-10-12 新增）。
     * 供前端「进度反馈」入口使用——POST /feedback 返回 R&lt;Void&gt; 拿不到 id，无法再调 submit。
     */
    @PostMapping("/feedback/apply")
    public R<Void> applyFeedback(@RequestBody BizScheduleFeedback feedback) {
        feedbackService.saveAndSubmit(feedback);
        return R.ok();
    }
}
