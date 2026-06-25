package com.zwinsight.project.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 变更项目角色请求
 */
@Data
public class UpdateRolesRequest {

    /**
     * 项目角色列表
     */
    private List<String> projectRoles;
}
