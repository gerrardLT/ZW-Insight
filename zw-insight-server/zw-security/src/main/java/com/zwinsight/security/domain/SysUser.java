package com.zwinsight.security.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.desensitize.Desensitize;
import com.zwinsight.common.desensitize.DesensitizeType;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    private String password;
    private String realName;

    @Desensitize(type = DesensitizeType.PHONE)
    private String phone;

    @Desensitize(type = DesensitizeType.EMAIL)
    private String email;

    private String avatar;
    private Integer status;
    private Long orgId;
    private Long postId;

    /**
     * 机构名称（非持久化，列表查询后按 orgId 批量回填，供前端「所属机构」列展示）
     */
    @TableField(exist = false)
    private String orgName;
}
