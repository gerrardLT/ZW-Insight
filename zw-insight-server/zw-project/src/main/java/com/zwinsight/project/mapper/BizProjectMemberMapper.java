package com.zwinsight.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.project.domain.BizProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 按项目ID逻辑删除全部成员映射（项目删除级联，R7-02）。
     * <p>与 biz_project 主表保持同一逻辑删除语义（BaseEntity @TableLogic），
     * 使数据可追溯可恢复，同时消除「项目 deleted=1 但成员 deleted=0」的审计孤儿。
     * 线上取证（2026-09-18 Round 7）：372 个已删除项目遗留 336 条存活成员映射。</p>
     *
     * @param projectId 项目ID
     * @return 影响行数
     */
    @Update("UPDATE biz_project_member SET deleted = 1 WHERE project_id = #{projectId} AND deleted = 0")
    int logicDeleteByProjectId(@Param("projectId") Long projectId);
}
