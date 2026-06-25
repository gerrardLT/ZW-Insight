package com.zwinsight.project.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 添加项目成员请求
 */
@Data
public class ProjectMemberAddRequest {

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
}
