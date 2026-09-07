package com.zwinsight.project.controller;

import com.zwinsight.common.annotation.OperLog;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.project.domain.BizProjectWbsNode;
import com.zwinsight.project.service.ProjectWbsNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * WBS 节点管理接口（工作分解结构）。
 * <p>
 * 路径挂在项目下：{@code /api/v1/project/{projectId}/wbs/nodes}，
 * 语义上 WBS 是项目的子资源，便于前端按项目上下文直接调用与权限收敛。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/project/{projectId}/wbs/nodes")
@RequiredArgsConstructor
@RequiresPermission("project:wbs:view")
public class ProjectWbsNodeController {

    private final ProjectWbsNodeService wbsNodeService;

    /**
     * 分页查询 WBS 节点
     */
    @GetMapping("/page")
    public R<PageResult<BizProjectWbsNode>> page(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(wbsNodeService.page(page, size, projectId, parentId, status, keyword));
    }

    /**
     * 获取 WBS 树形结构（一次性返回全树，前端本地展开/折叠）
     */
    @GetMapping("/tree")
    public R<List<BizProjectWbsNode>> getTree(@PathVariable Long projectId) {
        return R.ok(wbsNodeService.getTree(projectId));
    }

    /**
     * 下拉选择用扁平列表（仅 ACTIVE 节点）
     */
    @GetMapping("/select-list")
    public R<List<BizProjectWbsNode>> listForSelect(@PathVariable Long projectId) {
        return R.ok(wbsNodeService.listForSelect(projectId));
    }

    /**
     * 查询节点详情
     */
    @GetMapping("/{id}")
    public R<BizProjectWbsNode> getById(@PathVariable Long projectId, @PathVariable Long id) {
        return R.ok(wbsNodeService.getById(id));
    }

    /**
     * 查询直接子节点
     */
    @GetMapping("/{id}/children")
    public R<List<BizProjectWbsNode>> listChildren(@PathVariable Long projectId, @PathVariable Long id) {
        return R.ok(wbsNodeService.listChildren(id));
    }

    /**
     * 新增 WBS 节点（level 由服务端按父节点推导，忽略入参 level）
     */
    @PostMapping
    @RequiresPermission("project:wbs:add")
    @OperLog(module = "WBS管理", operType = "INSERT", description = "新增WBS节点")
    public R<BizProjectWbsNode> create(@PathVariable Long projectId,
                                       @RequestBody BizProjectWbsNode request) {
        // projectId 以路径为准，防止 body 与路径不一致造成的跨项目写入
        request.setProjectId(projectId);
        return R.ok(wbsNodeService.create(request));
    }

    /**
     * 更新 WBS 节点
     */
    @PutMapping("/{id}")
    @RequiresPermission("project:wbs:edit")
    @OperLog(module = "WBS管理", operType = "UPDATE", description = "更新WBS节点")
    public R<BizProjectWbsNode> update(@PathVariable Long projectId,
                                       @PathVariable Long id,
                                       @RequestBody BizProjectWbsNode request) {
        // 禁止通过 body 改 projectId（跨项目迁移需走专门流程）
        request.setProjectId(null);
        return R.ok(wbsNodeService.update(id, request));
    }

    /**
     * 删除 WBS 节点（逻辑删除；存在子节点时拒绝）
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("project:wbs:delete")
    @OperLog(module = "WBS管理", operType = "DELETE", description = "删除WBS节点")
    public R<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        wbsNodeService.delete(id);
        return R.ok();
    }

    /**
     * 批量删除 WBS 节点（任一失败整体回滚）
     */
    @PostMapping("/batch-delete")
    @RequiresPermission("project:wbs:delete")
    @OperLog(module = "WBS管理", operType = "DELETE", description = "批量删除WBS节点")
    public R<Integer> batchDelete(@PathVariable Long projectId, @RequestBody List<Long> ids) {
        return R.ok(wbsNodeService.batchDelete(ids));
    }
}
