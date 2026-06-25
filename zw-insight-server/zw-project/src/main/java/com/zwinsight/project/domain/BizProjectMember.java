package com.zwinsight.project.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目成员实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_project_member", autoResultMap = true)
public class BizProjectMember extends BaseEntity {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名称（冗余展示字段）
     */
    private String userName;

    /**
     * 项目角色列表（JSON 数组）
     * 例如: ["PROJECT_MANAGER", "CONSTRUCTOR"]
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> projectRoles;

    /**
     * 加入日期
     */
    private LocalDate joinDate;

    /**
     * 状态（1-正常 2-已失效）
     */
    private Integer status;
}
