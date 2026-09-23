package com.zwinsight.dashboard.risk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 项目责任人解析（风险六要素之"谁负责"）
 * <p>取项目 PROJECT_MANAGER 成员（状态正常）；无项目经理时返回 null，
 * 由台账如实呈现"责任人缺失"，不静默指定他人。</p>
 */
@Component
@RequiredArgsConstructor
public class ProjectOwnerResolver {

    private final BizProjectMemberMapper projectMemberMapper;

    /**
     * 解析项目经理（首个命中成员）
     *
     * @return [userId, userName]；无项目经理返回 null
     */
    public String[] resolveProjectManager(Long projectId) {
        if (projectId == null) {
            return null;
        }
        List<BizProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<BizProjectMember>()
                        .eq(BizProjectMember::getProjectId, projectId)
                        .eq(BizProjectMember::getStatus, 1)
                        .like(BizProjectMember::getProjectRoles, "PROJECT_MANAGER")
                        .orderByAsc(BizProjectMember::getId));
        if (members.isEmpty()) {
            return null;
        }
        BizProjectMember manager = members.get(0);
        return new String[]{
                manager.getUserId() != null ? String.valueOf(manager.getUserId()) : null,
                manager.getUserName()
        };
    }
}
