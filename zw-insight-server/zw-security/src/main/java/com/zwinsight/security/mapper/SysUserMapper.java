package com.zwinsight.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.security.domain.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username} AND deleted = 0")
    long countActiveByUsername(@Param("username") String username);

    @Select("SELECT COUNT(*) FROM sys_user WHERE phone = #{phone} AND deleted = 0")
    long countActiveByPhone(@Param("phone") String phone);

    // V2026_76 的 NULL guard 释放唯一键，保留原凭据供历史追溯，不拼接用户名。
    @Update("UPDATE sys_user SET status = 0, deleted = 1, updated_at = NOW(), version = version + 1 " +
            "WHERE id = #{userId} AND deleted = 0")
    int archiveForDelete(@Param("userId") Long userId);

    @Select("SELECT r.role_code FROM sys_user_role ur JOIN sys_role r ON ur.role_id = r.id WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT m.permission FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id JOIN sys_user_role ur ON ur.role_id = rm.role_id WHERE ur.user_id = #{userId} AND m.permission IS NOT NULL AND m.permission != ''")
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);
}
