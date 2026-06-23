package com.zwinsight.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.system.domain.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 根据角色ID列表查询菜单
     */
    List<SysMenu> selectMenusByRoleIds(@Param("roleIds") List<Long> roleIds);
}
