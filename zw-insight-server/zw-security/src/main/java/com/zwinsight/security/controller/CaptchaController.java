package com.zwinsight.security.controller;

import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zwinsight.common.result.R;
import com.zwinsight.security.dto.CaptchaVO;
import com.zwinsight.security.dto.SmsCaptchaDTO;
import com.zwinsight.security.service.CaptchaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 验证码控制器
 * 提供图形验证码、短信验证码与天爱（tianai-captcha）滑块验证码接口
 * <p>权限豁免说明：/captcha/** 为免登录接口，已在 WebMvcConfig EXCLUDE_PATHS
 * 白名单放行（AuthInterceptor 与 PermissionInterceptor 共用），
 * 不加 {@code @RequiresPermission}。</p>
 */
@RestController
@RequestMapping("/api/v1/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    /** TAC Web SDK 约定：4001 = 验证未通过（重新拉取），其余非 200 视为系统异常。 */
    private static final int TAC_VERIFY_FAILED = 4001;

    private final CaptchaService captchaService;
    private final ObjectMapper objectMapper;

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

    /** 获取天爱滑块挑战（背景图 + 拼图块）；不下发目标位置。 */
    @GetMapping("/slider")
    public R<ImageCaptchaVO> getSlider() {
        return R.ok(captchaService.generateSlider());
    }

    /**
     * 校验滑块行为轨迹（TAC Web SDK 原生载荷 {id, data: track}），
     * 成功返回一次性 sliderToken（登录时携带并由 AuthService 消费）。
     */
    @PostMapping("/slider/verify")
    public R<Map<String, String>> verifySlider(@RequestBody SliderVerifyRequest body) {
        ImageCaptchaTrack track = body == null ? null : toTrack(body.data());
        String token = body == null ? null : captchaService.verifySlider(body.id(), track);
        if (token == null) return R.fail(TAC_VERIFY_FAILED, "验证失败，请重试");
        return R.ok(Map.of("sliderToken", token));
    }

    private ImageCaptchaTrack toTrack(JsonNode raw) {
        if (raw == null || !raw.isObject()) return null;
        ObjectNode data = ((ObjectNode) raw).deepCopy();
        if (!normalizeTime(data, "startTime") || !normalizeTime(data, "stopTime")) return null;
        try {
            return objectMapper.treeToValue(data, ImageCaptchaTrack.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** TAC 1.4 发 ISO-8601；core 1.5.5 要 epoch ms。同时兼容数值载荷。 */
    private boolean normalizeTime(ObjectNode data, String field) {
        JsonNode value = data.get(field);
        if (value == null || value.isNull() || value.isNumber()) return value != null && !value.isNull();
        if (!value.isTextual()) return false;
        try {
            data.put(field, Instant.parse(value.textValue()).toEpochMilli());
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    /** TAC Web SDK 校验请求体。 */
    public record SliderVerifyRequest(String id, JsonNode data) {}
}
