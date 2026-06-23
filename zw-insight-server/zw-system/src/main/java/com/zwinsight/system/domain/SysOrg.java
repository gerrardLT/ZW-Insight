package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 机构实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_org")
public class SysOrg extends BaseEntity {

    /**
     * 机构名称
     */
    private String orgName;

    /**
     * 机构编码
     */
    private String orgCode;

    /**
     * 机构类型（COMPANY-公司 DEPARTMENT-部门）
     */
    private String orgType;

    /**
     * 父机构ID
     */
    private Long parentId;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 状态（1-启用 0-停用）
     */
    private Integer status;

    /**
     * 祖先路径（如：0,1,2）
     */
    private String ancestors;
}
