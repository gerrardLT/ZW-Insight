package com.zwinsight.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.security.domain.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("SELECT r.role_code FROM sys_user_role ur JOIN sys_role r ON ur.role_id = r.id WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT m.permission FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id JOIN sys_user_role ur ON ur.role_id = rm.role_id WHERE ur.user_id = #{userId} AND m.permission IS NOT NULL AND m.permission != ''")
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);
}
