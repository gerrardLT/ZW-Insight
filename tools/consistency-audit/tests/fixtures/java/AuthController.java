package com.zwinsight.security.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.security.dto.LoginRequest;
import com.zwinsight.security.dto.LoginResponse;
import com.zwinsight.security.service.AuthService;
import com.zwinsight.security.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final CaptchaService captchaService;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return R.ok(response);
    }

    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        authService.logout(extractToken(request));
        return R.ok();
    }

    @GetMapping("/captcha")
    public R<Map<String, String>> getCaptcha(@RequestParam String key) {
        String imageBase64 = captchaService.generateCaptcha(key);
        return R.ok(Map.of("image", imageBase64, "key", key));
    }

    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> params) {
        authService.changePassword(
            params.get("oldPassword"),
            params.get("newPassword")
        );
        return R.ok();
    }

    @GetMapping("/current-user")
    public R<Map<String, Object>> getCurrentUser() {
        return R.ok(authService.getCurrentUserInfo());
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
