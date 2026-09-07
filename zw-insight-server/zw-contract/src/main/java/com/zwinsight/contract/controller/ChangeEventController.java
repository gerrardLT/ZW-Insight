package com.zwinsight.contract.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.contract.domain.BizChangeEvent;
import com.zwinsight.contract.service.ChangeEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 变更事件管理接口。
 * <p>
 * 变更主链：<b>现场事件 → Change Event → 影响评估 → 审批 → 预算/合同/收入变更</b>。
 * </p>
 *
 * <h3>身份来源约定</h3>
 * <p>
 * 评估人 / 批准人一律取自服务端 SecurityContext（登录态），<b>不接受请求头或参数传入</b>。
 * 允许客户端指定"我是谁"等于放弃审计可信度——变更审批恰恰是最需要问责留痕的场景。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/contract/change-event")
@RequiredArgsConstructor
@RequiresPermission("contract:changeevent:view")
public class ChangeEventController {

    private final ChangeEventService changeEventService;

    /**
     * 分页查询变更事件
     */
    @GetMapping("/page")
    public R<PageResult<BizChangeEvent>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        return R.ok(changeEventService.page(page, size, projectId, status, sourceType, category, keyword));
    }

    /**
     * 查询详情
     */
    @GetMapping("/{id}")
    public R<BizChangeEvent> getById(@PathVariable Long id) {
        return R.ok(changeEventService.getById(id));
    }

    /**
     * 项目下待处理事件数（工作台角标）
     */
    @GetMapping("/open-count")
    public R<Long> countOpen(@RequestParam Long projectId) {
        return R.ok(changeEventService.countOpen(projectId));
    }

    /**
     * 项目下已批准变更的累计成本影响（成本主线看板「累计变更额」）
     */
    @GetMapping("/approved-cost-delta")
    public R<BigDecimal> sumApprovedCostDelta(@RequestParam Long projectId) {
        return R.ok(changeEventService.sumApprovedCostDelta(projectId));
    }

    /**
     * 登记变更事件（草稿）
     */
    @PostMapping
    @RequiresPermission("contract:changeevent:add")
    @OperLog(module = "变更管理", operType = "INSERT", description = "登记变更事件")
    public R<BizChangeEvent> create(@RequestBody BizChangeEvent request) {
        return R.ok(changeEventService.create(request));
    }

    /**
     * 更新变更事件（仅草稿/评估中可改）
     */
    @PutMapping("/{id}")
    @RequiresPermission("contract:changeevent:edit")
    @OperLog(module = "变更管理", operType = "UPDATE", description = "更新变更事件")
    public R<BizChangeEvent> update(@PathVariable Long id, @RequestBody BizChangeEvent request) {
        return R.ok(changeEventService.update(id, request));
    }

    /**
     * 转入评估中（现场登记完成，交商务测算）
     */
    @PostMapping("/{id}/start-assessment")
    @RequiresPermission("contract:changeevent:assess")
    @OperLog(module = "变更管理", operType = "UPDATE", description = "变更事件转入评估")
    public R<BizChangeEvent> startAssessment(@PathVariable Long id) {
        return R.ok(changeEventService.startAssessment(id));
    }

    /**
     * 提交影响评估（成本 + 工期 + 理由），流转至审批中
     */
    @PostMapping("/{id}/assessment")
    @RequiresPermission("contract:changeevent:assess")
    @OperLog(module = "变更管理", operType = "UPDATE", description = "提交变更影响评估")
    public R<BizChangeEvent> submitAssessment(@PathVariable Long id,
                                              @RequestBody BizChangeEvent.ImpactAssessment assessment) {
        return R.ok(changeEventService.submitAssessment(id, assessment));
    }

    /**
     * 批准变更事件（触发下游 CBS/合同/预算传导）
     *
     * @param comment 审批意见（可空）
     */
    @PostMapping("/{id}/approve")
    @RequiresPermission("contract:changeevent:approve")
    @OperLog(module = "变更管理", operType = "UPDATE", description = "批准变更事件")
    public R<BizChangeEvent> approve(@PathVariable Long id,
                                     @RequestParam(required = false) String comment) {
        return R.ok(changeEventService.approve(id, comment));
    }

    /**
     * 驳回变更事件（必须留原因）
     */
    @PostMapping("/{id}/reject")
    @RequiresPermission("contract:changeevent:reject")
    @OperLog(module = "变更管理", operType = "UPDATE", description = "驳回变更事件")
    public R<BizChangeEvent> reject(@PathVariable Long id, @RequestParam String rejectionReason) {
        return R.ok(changeEventService.reject(id, rejectionReason));
    }

    /**
     * 退回重新评估（保留事件连续性，不作废重登）
     */
    @PostMapping("/{id}/re-assess")
    @RequiresPermission("contract:changeevent:assess")
    @OperLog(module = "变更管理", operType = "UPDATE", description = "变更事件退回重评")
    public R<BizChangeEvent> reAssess(@PathVariable Long id,
                                      @RequestParam(required = false) String reason) {
        return R.ok(changeEventService.reAssess(id, reason));
    }

    /**
     * 作废变更事件（已批准的不可作废，须登记反向变更冲销）
     */
    @PostMapping("/{id}/cancel")
    @RequiresPermission("contract:changeevent:cancel")
    @OperLog(module = "变更管理", operType = "UPDATE", description = "作废变更事件")
    public R<BizChangeEvent> cancel(@PathVariable Long id,
                                    @RequestParam(required = false) String reason) {
        return R.ok(changeEventService.cancel(id, reason));
    }

    /**
     * 删除变更事件（仅草稿/已作废）
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("contract:changeevent:delete")
    @OperLog(module = "变更管理", operType = "DELETE", description = "删除变更事件")
    public R<Void> delete(@PathVariable Long id) {
        changeEventService.delete(id);
        return R.ok();
    }

    /**
     * 批量查询（跨模块回填展示字段用）
     */
    @PostMapping("/list-by-ids")
    public R<List<BizChangeEvent>> listByIds(@RequestBody List<Long> ids) {
        return R.ok(changeEventService.listByIds(ids));
    }
}
