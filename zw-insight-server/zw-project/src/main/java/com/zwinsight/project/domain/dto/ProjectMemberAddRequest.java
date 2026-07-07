package com.zwinsight.project.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 用户名称
     */
    private String userName;

    /**
     * 项目角色列表
     */
    @NotEmpty(message = "项目角色不能为空")
    private List<String> projectRoles;
}
