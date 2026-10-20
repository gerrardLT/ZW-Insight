package com.zwinsight.dashboard.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.dashboard.service.ProjectWorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 项目台账首屏聚合（S3.1）：摘要卡与当前页项目健康度一次返回。
 */
@RestController
@RequestMapping("/api/v1/dashboard/workbench")
@RequiredArgsConstructor
@RequiresPermission("project:view")
public class ProjectWorkbenchController {

    private final ProjectWorkbenchService projectWorkbenchService;

    @GetMapping("/project")
    public R<Map<String, Object>> project(@RequestParam(required = false) String projectIds) {
        return R.ok(projectWorkbenchService.summary(projectIds));
    }
}
