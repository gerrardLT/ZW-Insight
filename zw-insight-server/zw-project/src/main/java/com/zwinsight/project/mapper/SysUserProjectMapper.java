package com.zwinsight.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.project.domain.SysUserProject;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserProjectMapper extends BaseMapper<SysUserProject> {

    /**
     * 查询用户参与的所有项目ID列表
     */
    @Select("SELECT project_id FROM sys_user_project WHERE user_id = #{userId}")
    List<Long> selectProjectIdsByUserId(@Param("userId") Long userId);

    /**
     * 按项目ID物理删除全部用户-项目映射（项目删除级联，R7-02）。
     * <p>本表为纯关联表，<b>无 deleted 列</b>（见 10_V2026_07__p1_system_integrity.sql），
     * 无法走逻辑删除，只能物理删除。关联表无业务历史价值，且数据权限过滤
     * 直接读本表，留着已删项目的映射会让用户看到无权访问的项目范围。</p>
     * <p>线上取证（2026-09-18 Round 7）：1042 条映射中 372 条指向已删除项目、
     * 628 条指向物理不存在的项目（R6-03），占总量 96%。</p>
     *
     * @param projectId 项目ID
     * @return 影响行数
     */
    @Delete("DELETE FROM sys_user_project WHERE project_id = #{projectId}")
    int deleteByProjectId(@Param("projectId") Long projectId);
}
