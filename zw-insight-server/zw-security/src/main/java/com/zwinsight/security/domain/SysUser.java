package com.zwinsight.security.domain;

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
}
