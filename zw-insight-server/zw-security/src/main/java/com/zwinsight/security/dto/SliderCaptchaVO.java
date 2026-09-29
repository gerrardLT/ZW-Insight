package com.zwinsight.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 拼图滑块验证码挑战；图片字段均为 PNG data URL。 */
@Data
@AllArgsConstructor
public class SliderCaptchaVO {
    private String challengeId;
    private String backgroundImage;
    private String pieceImage;
    private int pieceY;
    private int imageWidth;
    private int imageHeight;
    private int pieceWidth;
}
