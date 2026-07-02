package com.zwinsight.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.domain.SysUserProject;
import com.zwinsight.project.domain.dto.ProjectMemberAddRequest;
import com.zwinsight.project.domain.enums.ProjectRoleEnum;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.project.mapper.SysUserProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ProjectMemberService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceTest {

    @Mock private BizProjectMemberMapper memberMapper;
    @Mock private SysUserProjectMapper userProjectMapper;

    @InjectMocks
    private ProjectMemberService memberService;

    // =====================================================================
    // addMember (ProjectMemberAddRequest)
    // =====================================================================

    @Test
    @DisplayName("添加成员：角色为空时抛异常")
    void testAddMember_emptyRoles_throws() {
        ProjectMemberAddRequest request = new ProjectMemberAddRequest();
        request.setUserId(200L);
        request.setUserName("张三");
        request.setProjectRoles(null);

        assertThatThrownBy(() -> memberService.addMember(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不能为空");
    }

    @Test
    @DisplayName("添加成员：同项目同用户重复添加时抛异常")
    void testAddMember_duplicate_throws() {
        ProjectMemberAddRequest request = new ProjectMemberAddRequest();
        request.setUserId(200L);
        request.setUserName("张三");
        request.setProjectRoles(List.of(ProjectRoleEnum.PROJECT_MANAGER.getCode()));

        when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> memberService.addMember(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已是本项目成员");
    }

    @Test
    @DisplayName("添加成员：正常添加并同步 sys_user_project")
    void testAddMember_success() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getTenantId).thenReturn(1L);

            ProjectMemberAddRequest request = new ProjectMemberAddRequest();
            request.setUserId(200L);
            request.setUserName("张三");
            request.setProjectRoles(List.of(ProjectRoleEnum.PROJECT_MANAGER.getCode()));

            when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(memberMapper.insert(any(BizProjectMember.class))).thenReturn(1);
            // syncAddUserProject 中的 selectCount
            when(userProjectMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(userProjectMapper.insert(any(SysUserProject.class))).thenReturn(1);

            memberService.addMember(1L, request);

            verify(memberMapper).insert(argThat(m ->
                    m.getProjectId().equals(1L)
                            && m.getUserId().equals(200L)
                            && m.getStatus() == 1));
            verify(userProjectMapper).insert(any(SysUserProject.class));
        }
    }

    // =====================================================================
    // removeMember (projectId, userId)
    // =====================================================================

    @Test
    @DisplayName("移除成员：成员不存在时抛异常")
    void testRemoveMember_notFound_throws() {
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> memberService.removeMember(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是本项目成员");
    }

    @Test
    @DisplayName("移除成员：唯一项目经理保护")
    void testRemoveMember_lastPmProtected() {
        BizProjectMember member = new BizProjectMember();
        member.setId(1L);
        member.setProjectId(1L);
        member.setUserId(100L);
        member.setProjectRoles(List.of(ProjectRoleEnum.PROJECT_MANAGER.getCode()));
        member.setStatus(1);

        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);
        when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> memberService.removeMember(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要保留一名项目经理");
    }

    @Test
    @DisplayName("移除成员：非项目经理可正常移除")
    void testRemoveMember_nonPm_success() {
        BizProjectMember member = new BizProjectMember();
        member.setId(2L);
        member.setProjectId(1L);
        member.setUserId(200L);
        member.setProjectRoles(List.of("ENGINEER"));
        member.setStatus(1);

        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);
        when(memberMapper.deleteById(2L)).thenReturn(1);
        when(userProjectMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        memberService.removeMember(1L, 200L);

        verify(memberMapper).deleteById(2L);
        verify(userProjectMapper).delete(any(LambdaQueryWrapper.class));
    }

    // =====================================================================
    // updateRoles
    // =====================================================================

    @Test
    @DisplayName("变更角色：角色为空时抛异常")
    void testUpdateRoles_emptyRoles_throws() {
        assertThatThrownBy(() -> memberService.updateRoles(1L, 100L, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不能为空");
    }

    @Test
    @DisplayName("变更角色：移除唯一 PM 角色时拒绝")
    void testUpdateRoles_removeLastPm_throws() {
        BizProjectMember member = new BizProjectMember();
        member.setId(1L);
        member.setProjectId(1L);
        member.setUserId(100L);
        member.setProjectRoles(List.of(ProjectRoleEnum.PROJECT_MANAGER.getCode()));
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);
        when(memberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> memberService.updateRoles(1L, 100L, List.of("CONSTRUCTOR")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要保留一名项目经理");
    }

    @Test
    @DisplayName("变更角色：正常更新")
    void testUpdateRoles_success() {
        BizProjectMember member = new BizProjectMember();
        member.setId(1L);
        member.setProjectId(1L);
        member.setUserId(100L);
        member.setProjectRoles(List.of("CONSTRUCTOR"));
        when(memberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(member);

        List<String> newRoles = List.of("CONSTRUCTOR", "SAFETY_OFFICER");
        memberService.updateRoles(1L, 100L, newRoles);

        verify(memberMapper).updateById(argThat(m -> m.getProjectRoles().containsAll(newRoles)));
    }

    // =====================================================================
    // getMembers
    // =====================================================================

    @Test
    @DisplayName("获取成员列表")
    void testGetMembers() {
        BizProjectMember m1 = new BizProjectMember();
        m1.setUserId(100L);
        when(memberMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(m1));

        List<BizProjectMember> members = memberService.getMembers(1L);

        assertThat(members).hasSize(1);
    }
}
