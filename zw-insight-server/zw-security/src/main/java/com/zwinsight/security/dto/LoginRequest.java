package com.zwinsight.security.dto;

import lombok.Data;

/**
 * 登录请求 DTO
 * 支持密码登录和短信验证码登录两种方式
 */
@Data
public class LoginRequest {

    // ============ 通用字段 ============

    /**
     * 登录类型: PASSWORD（密码登录，默认）/ SMS（短信验证码登录）
     */
    private String loginType;

    /**
     * 租户编码（可选）
     */
    private String tenantCode;

    // ============ 密码登录字段 ============

    /**
     * 用户名（密码登录时必填）
     */
    private String username;

    /**
     * 密码（密码登录时必填）
     */
    private String password;

    // ============ 图形验证码字段（PC 端密码登录） ============

    /**
     * 图形验证码 UUID（新版）
     */
    private String captchaUuid;

    /**
     * 图形验证码输入值（新版）
     */
    private String captchaCode;

    /**
     * 验证码（旧版，保留向后兼容）
     */
    private String captcha;

    /**
     * 验证码 Key（旧版，保留向后兼容）
     */
    private String captchaKey;

    // ============ 短信验证码登录字段 ============

    /**
     * 手机号（短信验证码登录时必填）
     */
    private String phone;

    /**
     * 短信验证码（短信验证码登录时必填）
     */
    private String smsCode;

    /**
     * 获取有效的登录类型，默认为 PASSWORD
     */
    public String getEffectiveLoginType() {
        return (loginType != null && !loginType.isBlank()) ? loginType.toUpperCase() : "PASSWORD";
    }
}
