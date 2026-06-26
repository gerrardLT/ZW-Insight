package com.zwinsight.security.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.security.dto.ResetPasswordRequest;
import com.zwinsight.security.dto.SendCodeRequest;
import com.zwinsight.security.dto.VerifyCodeRequest;
import com.zwinsight.security.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 忘记密码 / 密码重置控制器。
 * <p>
 * 三步流程，全部为免登录接口（已在 {@code WebMvcConfig} 白名单放行）：
 * <ol>
 *   <li>{@code POST /send-code}   — 发送短信验证码（body: {phone}）</li>
 *   <li>{@code POST /verify-code} — 校验验证码（body: {phone, code}）</li>
 *   <li>{@code POST /reset}       — 重置密码（body: {phone, code, newPassword}）</li>
 * </ol>
 * 业务校验失败由 {@link PasswordResetService} 抛出 {@code BusinessException}，
 * 交由全局异常处理器统一转换为响应。
 */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * 发送密码重置短信验证码。
     */
    @PostMapping("/send-code")
    public R<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        passwordResetService.sendCode(request.getPhone());
        return R.ok();
    }

    /**
     * 校验短信验证码（非消费式）。
     */
    @PostMapping("/verify-code")
    public R<Void> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        passwordResetService.verifyCode(request.getPhone(), request.getCode());
        return R.ok();
    }

    /**
     * 重置密码。
     */
    @PostMapping("/reset")
    public R<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request.getPhone(), request.getCode(), request.getNewPassword());
        return R.ok();
    }
}
