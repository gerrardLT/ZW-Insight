package com.zwinsight.security.dto;

import lombok.Data;

/**
 * 图形验证码响应 VO
 */
@Data
public class CaptchaVO {

    /**
     * 验证码唯一标识
     */
    private String uuid;

    /**
     * Base64 编码的验证码图片（带 data:image/png;base64, 前缀）
     */
    private String imageBase64;

    public CaptchaVO(String uuid, String imageBase64) {
        this.uuid = uuid;
        this.imageBase64 = imageBase64;
    }
}
