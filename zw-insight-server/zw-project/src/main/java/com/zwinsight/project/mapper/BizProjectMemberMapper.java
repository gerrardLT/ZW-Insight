package com.zwinsight.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.project.domain.BizProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BizProjectMemberMapper extends BaseMapper<BizProjectMember> {

    /**
     * 分页查询项目成员（支持按角色筛选）
     * 使用 JSON_CONTAINS 在 project_roles JSON 数组中匹配角色
     */
    IPage<BizProjectMember> selectMemberPage(
            Page<BizProjectMember> page,
            @Param("projectId") Long projectId,
            @Param("role") String role
    );
}
