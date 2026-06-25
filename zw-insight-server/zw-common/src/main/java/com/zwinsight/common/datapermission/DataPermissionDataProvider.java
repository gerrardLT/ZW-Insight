package com.zwinsight.common.datapermission;

import java.util.List;

/**
 * 数据权限数据提供者接口
 * <p>
 * 定义数据权限处理所需的数据查询方法。
 * 该接口在 zw-common 模块中定义，具体实现由 zw-system 或其他业务模块提供。
 * </p>
 */
public interface DataPermissionDataProvider {

    /**
     * 获取用户的所有角色数据范围配置
     * <p>
     * 从 sys_role 表读取用户关联角色的 data_scope 字段值。
     * 每次调用实时查询数据库，不使用缓存，确保配置变更立即生效。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户所有角色的数据范围字符串列表（如 ["ALL", "DEPT", "SELF"]）
     */
    List<String> getUserDataScopes(Long userId);

    /**
     * 获取用户参与的项目ID列表
     * <p>从 sys_user_project 表查询用户作为成员参与的所有项目ID</p>
     *
     * @param userId 用户ID
     * @return 项目ID列表
     */
    List<Long> getUserProjectIds(Long userId);

    /**
     * 获取用户所属部门ID
     *
     * @param userId 用户ID
     * @return 部门ID，未设置时返回 null
     */
    Long getUserDeptId(Long userId);

    /**
     * 获取指定部门及其所有下级部门的ID列表
     * <p>递归查询部门树，返回当前部门和全部子孙部门ID</p>
     *
     * @param deptId 部门ID
     * @return 部门及子部门ID列表（包含传入的 deptId）
     */
    List<Long> getDeptAndChildIds(Long deptId);
}
