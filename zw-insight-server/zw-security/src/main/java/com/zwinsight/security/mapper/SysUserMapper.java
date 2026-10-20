package com.zwinsight.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.security.domain.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
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

    @Select("SELECT DISTINCT r.role_code FROM sys_user_role ur " +
            "JOIN sys_user u ON u.id = ur.user_id JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND u.status = 1 AND u.deleted = 0 AND u.tenant_id IS NOT NULL " +
            "AND r.status = 1 AND r.deleted = 0 AND (r.tenant_id = u.tenant_id OR r.tenant_id IS NULL)")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT m.permission FROM sys_role_menu rm " +
            "JOIN sys_menu m ON rm.menu_id = m.id JOIN sys_user_role ur ON ur.role_id = rm.role_id " +
            "JOIN sys_user u ON u.id = ur.user_id JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND u.status = 1 AND u.deleted = 0 AND u.tenant_id IS NOT NULL " +
            "AND r.status = 1 AND r.deleted = 0 AND (r.tenant_id = u.tenant_id OR r.tenant_id IS NULL) " +
            "AND m.status = 1 AND m.deleted = 0 AND (m.tenant_id = u.tenant_id OR m.tenant_id IS NULL) " +
            "AND m.permission IS NOT NULL AND m.permission != ''")
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);

    /**
     * 查询指定租户内启用且未删除的用户 ID（抄送人校验用）。
     * <p>sys_ 表不参与 MyBatis-Plus 租户拦截器，必须显式带 tenant_id 条件；
     * {@link SysUser} 本身没有 tenantId 字段，不能靠 selectBatchIds 后内存比对。</p>
     */
    @Select("<script>SELECT id FROM sys_user WHERE tenant_id = #{tenantId} AND status = 1 AND deleted = 0 " +
            "AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> selectActiveIdsByTenant(@Param("tenantId") Long tenantId, @Param("ids") Collection<Long> ids);

    /**
     * 按角色编码解析租户内的有效用户ID（候选组任务通知用）。
     * <p>与 {@link #selectRoleCodesByUserId} 同口径：仅统计启用且未删除的用户，
     * 角色需启用/未删除且属于该租户或为共享角色（tenant_id IS NULL）。</p>
     */
    @Select("<script>SELECT DISTINCT u.id FROM sys_user u " +
            "JOIN sys_user_role ur ON ur.user_id = u.id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE u.status = 1 AND u.deleted = 0 AND u.tenant_id = #{tenantId} " +
            "AND r.status = 1 AND r.deleted = 0 " +
            "AND (r.tenant_id = #{tenantId} OR r.tenant_id IS NULL) " +
            "AND r.role_code IN " +
            "<foreach collection='roleCodes' item='rc' open='(' separator=',' close=')'>#{rc}</foreach>" +
            "</script>")
    List<Long> selectUserIdsByRoleCodes(@Param("roleCodes") List<String> roleCodes,
                                        @Param("tenantId") Long tenantId);

    /** 查询租户内全部启用且未删除的用户ID（通知/公告广播用） */
    @Select("SELECT id FROM sys_user WHERE tenant_id = #{tenantId} AND status = 1 AND deleted = 0")
    List<Long> selectActiveUserIds(@Param("tenantId") Long tenantId);
}
