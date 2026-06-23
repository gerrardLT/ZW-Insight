package com.zwinsight.project.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 项目成员实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_project_member")
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
     * 用户名称
     */
    private String userName;

    /**
     * 角色类型
     */
    private String roleType;

    /**
     * 加入日期
     */
    private LocalDate joinDate;

    /**
     * 状态（1-在岗 0-离岗）
     */
    private Integer status;
}
