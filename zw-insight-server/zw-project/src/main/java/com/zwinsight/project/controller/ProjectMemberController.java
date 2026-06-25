package com.zwinsight.project.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.project.domain.dto.ProjectMemberAddRequest;
import com.zwinsight.project.domain.dto.UpdateRolesRequest;
import com.zwinsight.project.domain.vo.ProjectMemberVO;
import com.zwinsight.project.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 项目成员管理接口
 */
@RestController
@RequestMapping("/api/v1/project/{projectId}/member")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    /**
     * 成员列表（分页 + 按角色筛选）
     */
    @GetMapping
    public R<PageResult<ProjectMemberVO>> listMembers(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role) {
        return R.ok(memberService.listMembers(projectId, role, page, size));
    }

    /**
     * 添加项目成员
     */
    @PostMapping
    public R<Void> addMember(
            @PathVariable Long projectId,
            @RequestBody ProjectMemberAddRequest request) {
        memberService.addMember(projectId, request);
        return R.ok();
    }

    /**
     * 移除项目成员
     */
    @DeleteMapping("/{userId}")
    public R<Void> removeMember(
            @PathVariable Long projectId,
            @PathVariable Long userId) {
        memberService.removeMember(projectId, userId);
        return R.ok();
    }

    /**
     * 变更成员角色
     */
    @PutMapping("/{userId}/roles")
    public R<Void> updateRoles(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @RequestBody UpdateRolesRequest request) {
        memberService.updateRoles(projectId, userId, request.getProjectRoles());
        return R.ok();
    }
}
