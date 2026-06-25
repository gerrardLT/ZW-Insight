package com.zwinsight.security.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.security.dto.CaptchaVO;
import com.zwinsight.security.dto.LoginRequest;
import com.zwinsight.security.dto.LoginResponse;
import com.zwinsight.security.service.AuthService;
import com.zwinsight.security.service.AuthService.CaptchaVerifyException;
import com.zwinsight.security.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final CaptchaService captchaService;

    /**
     * 统一登录接口
     * 支持密码登录（带图形验证码）和短信验证码登录
     * <p>
     * R4.3: 验证码校验失败时，在错误响应中同时返回新的验证码
     */
    @PostMapping("/login")
    public R<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        try {
            LoginResponse response = authService.login(request, clientIp);
            return R.ok(response);
        } catch (CaptchaVerifyException e) {
            // R4.3: 验证码校验失败时返回新验证码
            CaptchaVO newCaptcha = e.getNewCaptcha();
            return new R<>(e.getCode(), e.getMessage(), newCaptcha);
        }
    }

    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            authService.logout(token);
        }
        return R.ok();
    }

    /**
     * 获取图形验证码（旧版接口，保留向后兼容）
     */
    @GetMapping("/captcha")
    public R<Map<String, String>> getCaptcha(@RequestParam String key) {
        String imageBase64 = captchaService.generateCaptcha(key);
        return R.ok(Map.of("image", imageBase64, "key", key));
    }

    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> params) {
        Long userId = SecurityContextHolder.getUserId();
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        authService.changePassword(userId, oldPassword, newPassword);
        return R.ok();
    }

    /**
     * 获取客户端 IP 地址
     * 优先从 X-Forwarded-For 头获取（反向代理场景），否则使用 RemoteAddr
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
