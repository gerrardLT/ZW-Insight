package com.zwinsight.project.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.service.ProjectMemberService;
import com.zwinsight.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目管理接口
 */
@RestController
@RequestMapping("/api/v1/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService memberService;

    @GetMapping
    public R<PageResult<BizProject>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectType) {
        return R.ok(projectService.page(page, size, projectName, status, projectType));
    }

    @GetMapping("/page")
    public R<PageResult<BizProject>> pageAlias(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectType) {
        return page(page, size, projectName, status, projectType);
    }

    @GetMapping("/{id}")
    public R<BizProject> getById(@PathVariable Long id) {
        return R.ok(projectService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody BizProject project) {
        projectService.save(project);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizProject project) {
        project.setId(id);
        projectService.update(project);
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

    @PutMapping("/{id}/submit")
    public R<Void> submitByPut(@PathVariable Long id) {
        return submit(id);
    }

    @GetMapping("/{id}/members")
    public R<List<BizProjectMember>> getMembers(@PathVariable Long id) {
        return R.ok(memberService.getMembers(id));
    }

    @PostMapping("/{id}/members")
    public R<Void> addMember(@PathVariable Long id, @RequestBody BizProjectMember member) {
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
