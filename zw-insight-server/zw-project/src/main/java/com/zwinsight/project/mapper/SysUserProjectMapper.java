package com.zwinsight.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.project.domain.SysUserProject;
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
}
