package com.zwinsight.security.controller;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.R;
import com.zwinsight.security.dto.CaptchaVO;
import com.zwinsight.security.dto.SmsCaptchaDTO;
import com.zwinsight.security.dto.SliderCaptchaVO;
import com.zwinsight.security.service.CaptchaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 验证码控制器
 * 提供图形验证码和短信验证码接口
 * <p>权限豁免说明：/captcha/** 为免登录接口，已在 WebMvcConfig EXCLUDE_PATHS
 * 白名单放行（AuthInterceptor 与 PermissionInterceptor 共用），
 * 不加 {@code @RequiresPermission}。</p>
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

    /** 获取拼图滑块挑战；图片为 PNG data URL，不下发目标 x。 */
    @GetMapping("/slider")
    public R<SliderCaptchaVO> getSlider() {
        return R.ok(captchaService.generateSlider());
    }

    /**
     * 校验滑块位置，成功返回一次性 sliderToken（登录时携带）
     */
    @PostMapping("/slider/verify")
    public R<Map<String, String>> verifySlider(@RequestBody Map<String, Object> body) {
        Object cid = body.get("challengeId");
        Object x = body.get("x");
        Object pct = body.get("pct");
        if (cid == null || (x == null) == (pct == null)) {
            throw new BusinessException("challengeId 与 x/pct（二选一）为必填参数");
        }
        double position;
        try { position = Double.parseDouble(String.valueOf(x != null ? x : pct)); }
        catch (NumberFormatException e) { throw new BusinessException("参数非法"); }
        String token = x != null
                ? captchaService.verifySliderX(String.valueOf(cid), position)
                : captchaService.verifySlider(String.valueOf(cid), position);
        if (token == null) throw new BusinessException("验证失败，请重试");
        return R.ok(Map.of("sliderToken", token));
    }
}
