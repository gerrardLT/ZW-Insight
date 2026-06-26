package com.zwinsight.security.util;

import com.zwinsight.security.dto.DeviceInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 登录设备信息解析工具。
 *
 * <p>登录时从请求中提取设备信息用于记录登录设备（{@code sys_login_device}）。优先使用客户端
 * 显式上送的设备头（移动端 App 场景）：</p>
 * <ul>
 *   <li>{@code X-Device-Id}   设备唯一标识</li>
 *   <li>{@code X-Device-Name} 设备名称（如 iPhone 15 Pro）</li>
 *   <li>{@code X-Device-Os}   操作系统（iOS/Android/...）</li>
 * </ul>
 *
 * <p>当上述头缺失时（如 PC 浏览器登录），从 {@code User-Agent} 解析操作系统与浏览器名称，
 * 并基于 {@code User-Agent + 客户端 IP} 生成稳定的设备标识，保证
 * {@code sys_login_device.device_id}（NOT NULL）始终有有效值。</p>
 */
public final class DeviceInfoResolver {

    private static final String UNKNOWN_DEVICE = "未知设备";
    private static final String UNKNOWN_OS = "未知";

    private DeviceInfoResolver() {
    }

    /**
     * 解析当前请求的设备信息。
     *
     * @param request  当前 HTTP 请求
     * @param clientIp 客户端 IP（用于在无显式设备 ID 时生成稳定标识）
     * @return 设备信息（deviceId 必非空）
     */
    public static DeviceInfo resolve(HttpServletRequest request, String clientIp) {
        DeviceInfo info = new DeviceInfo();
        String userAgent = request != null ? request.getHeader("User-Agent") : null;

        String deviceId = header(request, "X-Device-Id");
        String deviceName = header(request, "X-Device-Name");
        String os = header(request, "X-Device-Os");

        if (!StringUtils.hasText(os)) {
            os = parseOs(userAgent);
        }
        if (!StringUtils.hasText(deviceName)) {
            deviceName = parseDeviceName(userAgent);
        }
        if (!StringUtils.hasText(deviceId)) {
            deviceId = stableDeviceId(userAgent, clientIp);
        }

        info.setDeviceId(deviceId);
        info.setDeviceName(deviceName);
        info.setOs(os);
        return info;
    }

    /**
     * 生成设备信息的可读描述，用于异地登录通知（如 "Chrome 浏览器 / Windows"）。
     */
    public static String describe(DeviceInfo info) {
        if (info == null) {
            return UNKNOWN_DEVICE;
        }
        String name = StringUtils.hasText(info.getDeviceName()) ? info.getDeviceName() : UNKNOWN_DEVICE;
        String os = StringUtils.hasText(info.getOs()) ? info.getOs() : UNKNOWN_OS;
        return name + " / " + os;
    }

    private static String header(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        return request.getHeader(name);
    }

    private static String parseOs(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return UNKNOWN_OS;
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            return "iOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("mac os") || ua.contains("macintosh")) {
            return "MacOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return UNKNOWN_OS;
    }

    private static String parseDeviceName(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return UNKNOWN_DEVICE;
        }
        String ua = userAgent.toLowerCase();
        // 浏览器判定顺序：Edge 优先于 Chrome（Edge UA 同时包含 chrome），其次 Firefox，最后 Safari
        if (ua.contains("edg/") || ua.contains("edge")) {
            return "Edge 浏览器";
        }
        if (ua.contains("firefox")) {
            return "Firefox 浏览器";
        }
        if (ua.contains("chrome") || ua.contains("crios")) {
            return "Chrome 浏览器";
        }
        if (ua.contains("safari")) {
            return "Safari 浏览器";
        }
        return UNKNOWN_DEVICE;
    }

    /**
     * 基于 User-Agent + IP 生成稳定的设备标识（SHA-256 前 32 位十六进制）。
     */
    private static String stableDeviceId(String userAgent, String clientIp) {
        String seed = (userAgent == null ? "" : userAgent) + "|" + (clientIp == null ? "" : clientIp);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return "web-" + sb.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 在标准 JVM 中必然可用；极端情况下退回到去除分隔符的种子，保证非空
            return "web-" + Integer.toHexString(seed.hashCode());
        }
    }
}
