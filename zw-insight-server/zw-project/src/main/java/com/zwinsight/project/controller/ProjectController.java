package com.zwinsight.project.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.domain.dto.ProjectCreateRequest;
import com.zwinsight.project.service.ProjectMemberService;
import com.zwinsight.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 项目管理接口
 */
@RestController
@RequestMapping("/api/v1/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService memberService;

    @GetMapping("/page")
    public R<PageResult<BizProject>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectType) {
        return R.ok(projectService.page(page, size, projectName, status, projectType));
    }

    @GetMapping("/{id}")
    public R<BizProject> getById(@PathVariable Long id) {
        return R.ok(projectService.getById(id));
    }

    @PostMapping
    public R<Void> save(@Valid @RequestBody ProjectCreateRequest request) {
        projectService.saveFromRequest(request);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectCreateRequest request) {
        projectService.updateFromRequest(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return R.ok();
    }

    @DeleteMapping("/batch")
    public R<Void> batchDelete(@RequestBody List<Long> ids) {
        projectService.batchDelete(ids);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        projectService.submit(id);
        return R.ok();
    }

    /**
     * 项目结项/关闭（校验所有条件后状态变更为 CLOSED）
     */
    @PostMapping("/{id}/close")
    public R<Void> closeProject(@PathVariable Long id) {
        projectService.closeProject(id);
        return R.ok();
    }

    /**
     * 结项条件预检（前端在发起结项前调用，提示用户哪些条件未满足）
     */
    @GetMapping("/{id}/close-check")
    public R<Map<String, Object>> closeCheck(@PathVariable Long id) {
        return R.ok(projectService.checkCloseConditions(id));
    }

    @GetMapping("/{id}/members")
    public R<List<BizProjectMember>> getMembers(@PathVariable Long id) {
        return R.ok(memberService.getMembers(id));
    }

    @PostMapping("/{id}/members")
    public R<Void> addMember(@PathVariable Long id, @Valid @RequestBody BizProjectMember member) {
        member.setProjectId(id);
        memberService.addMember(member);
        return R.ok();
    }

    @DeleteMapping("/members/{memberId}")
    public R<Void> removeMember(@PathVariable Long memberId) {
        memberService.removeMember(memberId);
        return R.ok();
    }
}
