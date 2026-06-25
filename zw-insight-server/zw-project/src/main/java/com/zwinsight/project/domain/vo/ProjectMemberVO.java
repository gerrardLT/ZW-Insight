package com.zwinsight.project.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目成员视图对象
 */
@Data
public class ProjectMemberVO {

    /**
     * 成员记录ID
     */
    private Long id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名称
     */
    private String userName;

    /**
     * 项目角色列表
     */
    private List<String> projectRoles;

    /**
     * 项目角色中文标签
     */
    private List<String> projectRoleLabels;

    /**
     * 加入日期
     */
    private LocalDate joinDate;

    /**
     * 状态（1-正常 2-已失效）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
