package com.zwinsight.finance.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.service.ProjectOnebookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 项目一本账接口（V2026_117 只读聚合，spec 阶段三）。
 * <p>六条线一次取全：收入/支出/成本/利润/资金/税务；全部实时聚合、不落库。
 * 挂载点：项目详情「项目经营」页签（项目经理与老板的一站式经营视图）。</p>
 * <p>权限：{@code project:view}——一本账是项目维度总览而非财务专页；
 * 数据行级范围不注入的原因见 {@link ProjectOnebookMapper} 类注释。</p>
 */
@RestController
@RequestMapping("/api/v1/project")
@RequiredArgsConstructor
@RequiresPermission("project:view")
public class ProjectOnebookController {

    private final ProjectOnebookService onebookService;

    /**
     * 项目一本账（六条线只读聚合）
     * <p>路径 {@code /{projectId}/onebook}：与 {@code ProjectController} 的 {@code /{id}} 家族
     * 无模式冲突（更长更具体的字面量段）。</p>
     */
    @GetMapping("/{projectId}/onebook")
    public R<Map<String, Object>> onebook(@PathVariable Long projectId) {
        return R.ok(onebookService.onebook(projectId));
    }
}
