package com.zwinsight.labor.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 班组
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_team")
public class BizTeam extends BaseEntity {

    /** 班组名称 */
    private String teamName;

    /** 项目ID */
    private Long projectId;

    /** 班组长姓名 */
    private String leaderName;

    /** 班组长电话 */
    private String leaderPhone;

    /** 工种 */
    private String workType;

    /** 状态（1-启用 0-停用） */
    private Integer status;

    /** 所属项目名称（透传，非本表字段） */
    @TableField(exist = false)
    private String projectName;

    /** 班组成员数（透传，从花名册聚合） */
    @TableField(exist = false)
    private Integer memberCount;
}
