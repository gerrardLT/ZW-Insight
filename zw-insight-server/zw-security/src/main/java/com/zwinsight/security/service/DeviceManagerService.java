package com.zwinsight.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.domain.SysLoginDevice;
import com.zwinsight.security.dto.DeviceInfo;
import com.zwinsight.security.dto.LoginDeviceVO;
import com.zwinsight.security.mapper.SysLoginDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 登录设备管理服务。
 *
 * <p>负责登录设备的记录、查询、远程注销，以及最大设备数自动淘汰。Token 失效复用
 * {@link PasswordResetService} 中相同的黑名单机制：写入黑名单
 * {@code token:blacklist:{SHA-256(token)}}（TTL = Token 剩余有效期），并删除会话
 * key {@code token:{token}}（等同强制登出，使 {@code AuthService.validateToken} 失败）。</p>
 *
 * <p>对应需求 8.1 ~ 8.6：</p>
 * <ul>
 *   <li>8.1 登录时记录设备信息（{@link #recordLogin}）</li>
 *   <li>8.2 查询活跃设备列表（{@link #listDevices}）</li>
 *   <li>8.3 远程注销设备并使 Token 失效（{@link #revokeDevice}）</li>
 *   <li>8.4 标识当前设备，禁止注销当前设备（{@link #listDevices} / {@link #revokeDevice}）</li>
 *   <li>8.5 超过最大设备数自动注销最早登录设备（{@link #autoEvictOldest}）</li>
 *   <li>8.6 Token 加入 Redis 黑名单（{@link #addToBlacklist}）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceManagerService {

    private final SysLoginDeviceMapper loginDeviceMapper;
    private final RedisUtils redisUtils;

    /** 登录 Token 在 Redis 中的会话前缀（与 {@code AuthService} / {@link PasswordResetService} 保持一致）。 */
    private static final String TOKEN_PREFIX = "token:";
    /** Token 黑名单前缀（设计文档约定）。 */
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /** 状态：活跃。 */
    private static final int STATUS_ACTIVE = 1;
    /** 状态：已注销。 */
    private static final int STATUS_REVOKED = 0;

    /** 会话 key 缺失或无 TTL 时，黑名单的兜底有效期（秒）—— 24 小时。 */
    private static final long DEFAULT_BLACKLIST_TTL_SECONDS = 24 * 60 * 60L;

    /** 最大活跃设备数，默认 5 台。 */
    @Value("${security.max-devices:5}")
    private int maxDevices;

    // ============ 8.1 记录登录设备 ============

    /**
     * 记录一次登录的设备信息。插入后触发 {@link #autoEvictOldest} 以保证活跃设备数不超过最大值。
     *
     * @param userId    用户 ID
     * @param device    设备信息（设备 ID、名称、操作系统）
     * @param token     本次登录 Token
     * @param ipAddress 登录 IP
     * @param location  IP 归属地（省份|城市），可为空
     */
    public void recordLogin(Long userId, DeviceInfo device, String token, String ipAddress, String location) {
        if (userId == null) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(400, "登录Token不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        SysLoginDevice record = new SysLoginDevice();
        record.setUserId(userId);
        if (device != null) {
            record.setDeviceId(device.getDeviceId());
            record.setDeviceName(device.getDeviceName());
            record.setOs(device.getOs());
        }
        record.setIpAddress(ipAddress);
        record.setLocation(location);
        record.setToken(token);
        record.setLoginTime(now);
        record.setLastActiveTime(now);
        record.setStatus(STATUS_ACTIVE);
        record.setCreatedAt(now);

        loginDeviceMapper.insert(record);
        log.info("[DEVICE] 记录登录设备: userId={}, deviceId={}, ip={}", userId, record.getDeviceId(), ipAddress);

        // 超过最大设备数时自动淘汰最早登录的设备
        autoEvictOldest(userId, maxDevices);
    }

    // ============ 8.2 / 8.4 查询设备列表 ============

    /**
     * 查询用户的活跃设备列表，并标识当前设备。
     *
     * @param userId       用户 ID
     * @param currentToken 当前请求的 Token，用于标记 {@code isCurrent}
     * @return 活跃设备 VO 列表（按登录时间倒序）
     */
    public List<LoginDeviceVO> listDevices(Long userId, String currentToken) {
        List<SysLoginDevice> devices = loginDeviceMapper.selectList(
                new LambdaQueryWrapper<SysLoginDevice>()
                        .eq(SysLoginDevice::getUserId, userId)
                        .eq(SysLoginDevice::getStatus, STATUS_ACTIVE)
                        .orderByDesc(SysLoginDevice::getLoginTime)
        );
        return devices.stream()
                .map(d -> toVO(d, currentToken))
                .collect(Collectors.toList());
    }

    // ============ 8.3 / 8.4 远程注销设备 ============

    /**
     * 注销指定设备：使其 Token 失效（加入黑名单 + 删除会话）。禁止注销当前正在使用的设备。
     *
     * @param userId         用户 ID
     * @param deviceRecordId 设备记录 ID（{@code sys_login_device.id}）
     * @param currentToken   当前请求 Token，用于校验是否为当前设备
     * @throws BusinessException 设备不存在 / 不属于该用户 / 注销当前设备
     */
    public void revokeDevice(Long userId, Long deviceRecordId, String currentToken) {
        SysLoginDevice device = loginDeviceMapper.selectById(deviceRecordId);
        if (device == null || !Objects.equals(device.getUserId(), userId)) {
            throw new BusinessException(404, "设备不存在");
        }
        // 禁止注销当前正在使用的设备
        if (currentToken != null && currentToken.equals(device.getToken())) {
            throw new BusinessException(400, "不能注销当前使用的设备");
        }

        device.setStatus(STATUS_REVOKED);
        loginDeviceMapper.updateById(device);
        addToBlacklist(device.getToken());
        log.info("[DEVICE] 注销设备: userId={}, deviceRecordId={}", userId, deviceRecordId);
    }

    // ============ 8.5 最大设备数自动淘汰 ============

    /**
     * 当用户活跃设备数超过 {@code max} 时，按登录时间升序逐个注销最早登录的设备，直至活跃数等于 {@code max}。
     *
     * @param userId 用户 ID
     * @param max    允许的最大活跃设备数
     */
    public void autoEvictOldest(Long userId, int max) {
        if (max <= 0) {
            return;
        }
        List<SysLoginDevice> activeDevices = loginDeviceMapper.selectList(
                new LambdaQueryWrapper<SysLoginDevice>()
                        .eq(SysLoginDevice::getUserId, userId)
                        .eq(SysLoginDevice::getStatus, STATUS_ACTIVE)
                        .orderByAsc(SysLoginDevice::getLoginTime)
        );
        int evictCount = activeDevices.size() - max;
        for (int i = 0; i < evictCount; i++) {
            SysLoginDevice oldest = activeDevices.get(i);
            oldest.setStatus(STATUS_REVOKED);
            loginDeviceMapper.updateById(oldest);
            addToBlacklist(oldest.getToken());
            log.info("[DEVICE] 超过最大设备数({}), 自动注销最早登录设备: userId={}, deviceRecordId={}",
                    max, userId, oldest.getId());
        }
    }

    // ============ 8.6 Token 加入黑名单 ============

    /**
     * 将 Token 加入 Redis 黑名单：黑名单 key 的 TTL = 会话 key {@code token:{token}} 的剩余有效期
     * （查不到或已过期时使用兜底 TTL），随后删除会话 key 实现强制登出。
     *
     * @param token 待失效的 Token
     */
    public void addToBlacklist(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String sessionKey = TOKEN_PREFIX + token;
        Long ttlSeconds = redisUtils.getExpire(sessionKey, TimeUnit.SECONDS);
        long blacklistTtl = (ttlSeconds != null && ttlSeconds > 0) ? ttlSeconds : DEFAULT_BLACKLIST_TTL_SECONDS;
        redisUtils.set(TOKEN_BLACKLIST_PREFIX + sha256(token), "1", blacklistTtl, TimeUnit.SECONDS);
        // 删除会话 key，等同强制登出（AuthService.validateToken 将失败）
        redisUtils.delete(sessionKey);
    }

    // ============ 私有辅助方法 ============

    private LoginDeviceVO toVO(SysLoginDevice device, String currentToken) {
        LoginDeviceVO vo = new LoginDeviceVO();
        vo.setId(device.getId());
        vo.setDeviceName(device.getDeviceName());
        vo.setOs(device.getOs());
        vo.setIpAddress(device.getIpAddress());
        vo.setLocation(device.getLocation());
        vo.setLoginTime(device.getLoginTime());
        vo.setLastActiveTime(device.getLastActiveTime());
        vo.setStatus(device.getStatus());
        vo.setIsCurrent(currentToken != null && currentToken.equals(device.getToken()));
        return vo;
    }

    /**
     * 计算 Token 的 SHA-256 十六进制摘要，用作黑名单 key，避免超长 key。
     * 与 {@link PasswordResetService} 保持一致。
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(500, "Token 摘要计算失败", e);
        }
    }
}
