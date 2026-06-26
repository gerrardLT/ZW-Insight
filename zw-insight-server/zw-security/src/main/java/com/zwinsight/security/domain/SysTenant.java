package com.zwinsight.security.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zwinsight.common.desensitize.Desensitize;
import com.zwinsight.common.desensitize.DesensitizeType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "sys_tenant", autoResultMap = true)
public class SysTenant implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String contactName;

    @Desensitize(type = DesensitizeType.PHONE)
    private String contactPhone;

    @Desensitize(type = DesensitizeType.ADDRESS)
    private String address;
    private Long tenantTypeId;

    /**
     * 租户状态：1-正常 2-已停用 3-已过期
     */
    private Integer status;

    /**
     * 原到期日（保留兼容）
     */
    private LocalDate expireDate;

    private String secretKey;

    /**
     * 用户类型：TRIAL/STANDARD/ENTERPRISE
     */
    private String userType;

    /**
     * 服务开始日期
     */
    private LocalDate startDate;

    /**
     * 服务到期日期
     */
    private LocalDate endDate;

    /**
     * 最大用户数
     */
    private Integer maxUsers;

    /**
     * 已授权功能模块编码列表（JSON数组）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> modules;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
