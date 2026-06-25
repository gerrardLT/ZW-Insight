package com.zwinsight.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.domain.SysUserProject;
import com.zwinsight.project.domain.dto.ProjectMemberAddRequest;
import com.zwinsight.project.domain.enums.ProjectRoleEnum;
import com.zwinsight.project.domain.vo.ProjectMemberVO;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.project.mapper.SysUserProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目成员服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final BizProjectMemberMapper memberMapper;
    private final SysUserProjectMapper userProjectMapper;

    /**
     * 获取项目成员列表（旧接口兼容）
     */
    public List<BizProjectMember> getMembers(Long projectId) {
        LambdaQueryWrapper<BizProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMember::getProjectId, projectId)
                .eq(BizProjectMember::getStatus, 1)
                .orderByDesc(BizProjectMember::getCreatedAt);
        return memberMapper.selectList(wrapper);
    }

    /**
     * 添加项目成员（含唯一性校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long projectId, ProjectMemberAddRequest request) {
        // 校验角色合法性
        validateRoles(request.getProjectRoles());

        // 唯一性校验：同项目同用户不可重复
        LambdaQueryWrapper<BizProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMember::getProjectId, projectId)
                .eq(BizProjectMember::getUserId, request.getUserId());
        Long count = memberMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(400, "该用户已是本项目成员");
        }

        // 创建成员记录
        BizProjectMember member = new BizProjectMember();
        member.setProjectId(projectId);
        member.setUserId(request.getUserId());
        member.setUserName(request.getUserName());
        member.setProjectRoles(request.getProjectRoles());
        member.setJoinDate(LocalDate.now());
        member.setStatus(1);
        memberMapper.insert(member);

        // 同步 sys_user_project 表
        syncAddUserProject(request.getUserId(), projectId);

        log.info("成员添加成功: projectId={}, userId={}, roles={}", projectId, request.getUserId(), request.getProjectRoles());
    }

    /**
     * 添加项目成员（旧接口兼容 - 内部调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addMember(BizProjectMember member) {
        // 唯一性校验
        LambdaQueryWrapper<BizProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMember::getProjectId, member.getProjectId())
                .eq(BizProjectMember::getUserId, member.getUserId());
        Long count = memberMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(400, "该用户已是本项目成员");
        }

        if (member.getJoinDate() == null) {
            member.setJoinDate(LocalDate.now());
        }
        if (member.getStatus() == null) {
            member.setStatus(1);
        }
        memberMapper.insert(member);

        // 同步 sys_user_project 表
        syncAddUserProject(member.getUserId(), member.getProjectId());
    }

    /**
     * 移除项目成员（含唯一项目经理保护校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long projectId, Long userId) {
        // 查找成员记录
        BizProjectMember member = findMember(projectId, userId);

        // 检查该成员是否为项目经理
        if (member.getProjectRoles() != null && member.getProjectRoles().contains(ProjectRoleEnum.PROJECT_MANAGER.getCode())) {
            // 检查项目中是否还有其他项目经理
            long pmCount = countProjectManagers(projectId);
            if (pmCount <= 1) {
                throw new BusinessException(400, "项目至少需要保留一名项目经理");
            }
        }

        // 逻辑删除成员
        memberMapper.deleteById(member.getId());

        // 同步删除 sys_user_project 记录
        syncRemoveUserProject(userId, projectId);

        log.info("成员移除成功: projectId={}, userId={}", projectId, userId);
    }

    /**
     * 移除项目成员（旧接口兼容 - 按成员ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long memberId) {
        BizProjectMember member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException("成员记录不存在");
        }

        // 检查唯一项目经理保护
        if (member.getProjectRoles() != null && member.getProjectRoles().contains(ProjectRoleEnum.PROJECT_MANAGER.getCode())) {
            long pmCount = countProjectManagers(member.getProjectId());
            if (pmCount <= 1) {
                throw new BusinessException(400, "项目至少需要保留一名项目经理");
            }
        }

        memberMapper.deleteById(memberId);
        syncRemoveUserProject(member.getUserId(), member.getProjectId());
    }

    /**
     * 变更成员角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRoles(Long projectId, Long userId, List<String> roles) {
        // 校验角色合法性
        validateRoles(roles);

        // 查找成员记录
        BizProjectMember member = findMember(projectId, userId);

        // 如果原来有 PROJECT_MANAGER 但新角色列表没有，检查是否为唯一 PM
        boolean wasManager = member.getProjectRoles() != null
                && member.getProjectRoles().contains(ProjectRoleEnum.PROJECT_MANAGER.getCode());
        boolean willBeManager = roles.contains(ProjectRoleEnum.PROJECT_MANAGER.getCode());

        if (wasManager && !willBeManager) {
            long pmCount = countProjectManagers(projectId);
            if (pmCount <= 1) {
                throw new BusinessException(400, "项目至少需要保留一名项目经理");
            }
        }

        // 更新角色
        member.setProjectRoles(roles);
        memberMapper.updateById(member);

        log.info("角色变更成功: projectId={}, userId={}, newRoles={}", projectId, userId, roles);
    }

    /**
     * 分页查询项目成员（支持按角色筛选）
     */
    public PageResult<ProjectMemberVO> listMembers(Long projectId, String role, int page, int size) {
        Page<BizProjectMember> pageParam = new Page<>(page, size);
        IPage<BizProjectMember> result = memberMapper.selectMemberPage(pageParam, projectId, role);

        // 转换为 VO
        List<ProjectMemberVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult<ProjectMemberVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    /**
     * 获取用户参与的项目 ID 列表（供数据权限模块使用）
     */
    public List<Long> getUserProjectIds(Long userId) {
        return userProjectMapper.selectProjectIdsByUserId(userId);
    }

    /**
     * 用户停用时标记所有成员为已失效
     */
    @Transactional(rollbackFor = Exception.class)
    public void deactivateByUserId(Long userId) {
        LambdaUpdateWrapper<BizProjectMember> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BizProjectMember::getUserId, userId)
                .eq(BizProjectMember::getStatus, 1)
                .set(BizProjectMember::getStatus, 2);
        memberMapper.update(null, wrapper);

        // 同步删除 sys_user_project 中该用户所有记录
        LambdaQueryWrapper<SysUserProject> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(SysUserProject::getUserId, userId);
        userProjectMapper.delete(delWrapper);

        log.info("用户成员已全部标记失效: userId={}", userId);
    }

    /**
     * 在项目创建时自动将创建人添加为项目经理
     */
    @Transactional(rollbackFor = Exception.class)
    public void addCreatorAsProjectManager(Long projectId, Long userId, String userName) {
        BizProjectMember member = new BizProjectMember();
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setUserName(userName);
        member.setProjectRoles(List.of(ProjectRoleEnum.PROJECT_MANAGER.getCode()));
        member.setJoinDate(LocalDate.now());
        member.setStatus(1);
        memberMapper.insert(member);

        // 同步 sys_user_project
        syncAddUserProject(userId, projectId);

        log.info("创建人自动添加为项目经理: projectId={}, userId={}", projectId, userId);
    }

    // ======================== 私有方法 ========================

    /**
     * 校验角色列表合法性
     */
    private void validateRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException(400, "项目角色不能为空");
        }
        for (String role : roles) {
            if (!ProjectRoleEnum.isValid(role)) {
                throw new BusinessException(400, "无效的项目角色: " + role);
            }
        }
    }

    /**
     * 查找项目成员
     */
    private BizProjectMember findMember(Long projectId, Long userId) {
        LambdaQueryWrapper<BizProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMember::getProjectId, projectId)
                .eq(BizProjectMember::getUserId, userId);
        BizProjectMember member = memberMapper.selectOne(wrapper);
        if (member == null) {
            throw new BusinessException(400, "该用户不是本项目成员");
        }
        return member;
    }

    /**
     * 统计项目中项目经理的数量
     */
    private long countProjectManagers(Long projectId) {
        LambdaQueryWrapper<BizProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMember::getProjectId, projectId)
                .eq(BizProjectMember::getStatus, 1)
                .apply("JSON_CONTAINS(project_roles, '\"PROJECT_MANAGER\"')");
        return memberMapper.selectCount(wrapper);
    }

    /**
     * 同步添加 sys_user_project 记录
     */
    private void syncAddUserProject(Long userId, Long projectId) {
        // 先检查是否已存在
        LambdaQueryWrapper<SysUserProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserProject::getUserId, userId)
                .eq(SysUserProject::getProjectId, projectId);
        Long count = userProjectMapper.selectCount(wrapper);
        if (count > 0) {
            return;
        }

        SysUserProject userProject = new SysUserProject();
        userProject.setUserId(userId);
        userProject.setProjectId(projectId);
        userProject.setTenantId(SecurityContextHolder.getTenantId());
        userProject.setCreatedAt(LocalDateTime.now());
        userProjectMapper.insert(userProject);
    }

    /**
     * 同步删除 sys_user_project 记录
     */
    private void syncRemoveUserProject(Long userId, Long projectId) {
        LambdaQueryWrapper<SysUserProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserProject::getUserId, userId)
                .eq(SysUserProject::getProjectId, projectId);
        userProjectMapper.delete(wrapper);
    }

    /**
     * 实体转 VO
     */
    private ProjectMemberVO convertToVO(BizProjectMember member) {
        ProjectMemberVO vo = new ProjectMemberVO();
        vo.setId(member.getId());
        vo.setProjectId(member.getProjectId());
        vo.setUserId(member.getUserId());
        vo.setUserName(member.getUserName());
        vo.setProjectRoles(member.getProjectRoles());
        vo.setJoinDate(member.getJoinDate());
        vo.setStatus(member.getStatus());
        vo.setCreatedAt(member.getCreatedAt());

        // 转换角色中文标签
        if (member.getProjectRoles() != null) {
            List<String> labels = new ArrayList<>();
            for (String roleCode : member.getProjectRoles()) {
                try {
                    labels.add(ProjectRoleEnum.fromCode(roleCode).getLabel());
                } catch (IllegalArgumentException e) {
                    labels.add(roleCode);
                }
            }
            vo.setProjectRoleLabels(labels);
        }

        return vo;
    }
}
