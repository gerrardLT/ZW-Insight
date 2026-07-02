package com.zwinsight.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-项目关联 Mapper（仅供数据权限模块使用）
 * <p>
 * 直接查询 sys_user_project 表，避免 zw-system 与 zw-project 模块循环依赖。
 * </p>
 */
@Mapper
public interface DataPermUserProjectMapper {

    /**
     * 查询用户参与的所有项目ID列表
     *
     * @param userId 用户ID
     * @return 项目ID列表
     */
    @Select("SELECT project_id FROM sys_user_project WHERE user_id = #{userId}")
    List<Long> selectProjectIdsByUserId(@Param("userId") Long userId);
}
