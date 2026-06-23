package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志实体
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 登录人姓名
     */
    private String loginName;

    /**
     * 登录账号
     */
    private String loginAccount;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 登录时间
     */
    private LocalDateTime loginTime;

    /**
     * 租户ID
     */
    private Long tenantId;
}
