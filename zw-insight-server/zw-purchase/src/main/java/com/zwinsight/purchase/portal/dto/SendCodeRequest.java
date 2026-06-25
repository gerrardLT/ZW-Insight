package com.zwinsight.purchase.portal.dto;

import lombok.Data;

/**
 * 发送验证码请求
 */
@Data
public class SendCodeRequest {

    /**
     * 手机号
     */
    private String phone;
}
