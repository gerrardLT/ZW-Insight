package com.zwinsight.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目成员服务
 */
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final BizProjectMemberMapper memberMapper;

    /**
     * 获取项目成员列表
     */
    public List<BizProjectMember> getMembers(Long projectId) {
        LambdaQueryWrapper<BizProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMember::getProjectId, projectId)
                .orderByDesc(BizProjectMember::getCreatedAt);
        return memberMapper.selectList(wrapper);
    }

    /**
     * 添加项目成员
     */
    public void addMember(BizProjectMember member) {
        // 检查是否已存在
        LambdaQueryWrapper<BizProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMember::getProjectId, member.getProjectId())
                .eq(BizProjectMember::getUserId, member.getUserId());
        Long count = memberMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("该成员已在项目中");
        }
        memberMapper.insert(member);
    }

    /**
     * 移除项目成员
     */
    public void removeMember(Long memberId) {
        memberMapper.deleteById(memberId);
    }
}
