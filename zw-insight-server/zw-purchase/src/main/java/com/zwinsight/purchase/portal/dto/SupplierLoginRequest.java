package com.zwinsight.purchase.portal.dto;

import lombok.Data;

/**
 * 供应商验证码登录请求
 */
@Data
public class SupplierLoginRequest {

    /**
     * 手机号
     */
    private String phone;

    /**
     * 短信验证码
     */
    private String code;
}
