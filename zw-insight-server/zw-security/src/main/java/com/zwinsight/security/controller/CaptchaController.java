package com.zwinsight.security.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.security.dto.CaptchaVO;
import com.zwinsight.security.dto.SmsCaptchaDTO;
import com.zwinsight.security.service.CaptchaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 验证码控制器
 * 提供图形验证码和短信验证码接口
 */
@RestController
@RequestMapping("/api/v1/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /**
     * 获取图形验证码
     * 返回 Base64 编码的验证码图片和 UUID
     */
    @GetMapping("/image")
    public R<CaptchaVO> getImageCaptcha() {
        CaptchaVO captchaVO = captchaService.generateImageCaptcha();
        return R.ok(captchaVO);
    }

    /**
     * 发送短信验证码
     *
     * @param dto 包含手机号的请求体
     */
    @PostMapping("/sms")
    public R<Void> sendSmsCode(@Valid @RequestBody SmsCaptchaDTO dto) {
        captchaService.sendSmsCode(dto.getPhone());
        return R.ok();
    }
}
