package com.zwinsight.system.dto;

import lombok.Data;

import java.util.List;

/**
 * 租户创建请求
 */
@Data
public class TenantCreateRequest {

    /** 租户名称 */
    private String tenantName;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人电话 */
    private String contactPhone;

    /** 地址 */
    private String address;

    /** 用户类型：TRIAL/STANDARD/ENTERPRISE */
    private String userType;

    /** 自定义有效天数（ENTERPRISE类型使用） */
    private Integer customDays;

    /** 最大用户数（不传则使用默认值） */
    private Integer maxUsers;

    /** 功能模块编码列表 */
    private List<String> modules;

    /** 管理员用户名 */
    private String adminUsername;

    /** 管理员密码 */
    private String adminPassword;

    /** 管理员手机号 */
    private String adminPhone;
}
