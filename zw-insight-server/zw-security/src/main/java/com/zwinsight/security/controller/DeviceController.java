package com.zwinsight.security.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.security.dto.LoginDeviceVO;
import com.zwinsight.security.service.DeviceManagerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录设备管理控制器。
 *
 * <p>映射 {@code /api/v1/user/devices}，与 {@code AuthController}（{@code /api/v1/auth}）
 * 保持同一前缀约定，从而被 {@code WebMvcConfig} 的 {@code AuthInterceptor}（拦截
 * {@code /api/**}）保护，请求线程上下文中已注入当前登录用户 ID。</p>
 *
 * <p>对应需求：</p>
 * <ul>
 *   <li>8.2 查询当前用户活跃设备列表（{@link #list}）</li>
 *   <li>8.3 远程注销指定设备并使其 Token 失效（{@link #revoke}）</li>
 *   <li>8.4 标识当前设备，禁止注销当前设备（由 {@link DeviceManagerService} 校验）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/user/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceManagerService deviceManagerService;

    /**
     * 查询当前登录用户的活跃设备列表，并标识当前设备（需求 8.2 / 8.4）。
     *
     * @param request 当前 HTTP 请求，用于提取当前 Token
     * @return 活跃设备列表（按登录时间倒序，含 isCurrent 标识）
     */
    @GetMapping("/list")
    public R<List<LoginDeviceVO>> list(HttpServletRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        String currentToken = extractToken(request);
        return R.ok(deviceManagerService.listDevices(userId, currentToken));
    }

    /**
     * 远程注销指定设备：使其 Token 加入黑名单并删除会话（需求 8.3）。
     * 禁止注销当前正在使用的设备（需求 8.4，由 service 层校验）。
     *
     * @param deviceId 设备记录 ID（{@code sys_login_device.id}）
     * @param request  当前 HTTP 请求，用于提取当前 Token
     * @return 操作结果
     */
    @DeleteMapping("/{deviceId}")
    public R<Void> revoke(@PathVariable("deviceId") Long deviceId, HttpServletRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        String currentToken = extractToken(request);
        deviceManagerService.revokeDevice(userId, deviceId, currentToken);
        return R.ok();
    }

    /**
     * 从 {@code Authorization} 请求头提取 Bearer Token。
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
