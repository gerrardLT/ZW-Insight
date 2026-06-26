package com.zwinsight.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.LoginLocationNotifyEvent;
import com.zwinsight.security.domain.SysLoginDevice;
import com.zwinsight.security.mapper.SysLoginDeviceMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 异地登录检测服务。
 *
 * <p>基于 ip2region 本地离线 IP 地址库（{@code ip2region.xdb}）解析登录 IP 的归属地，
 * 与用户最近一次登录归属地比对，不一致时通过 Spring 事件机制发布
 * {@link LoginLocationNotifyEvent}，由 message 模块监听并发送站内消息 + WebSocket 推送，
 * 从而避免 security 模块直接依赖 message 模块。</p>
 *
 * <h3>xdb 数据文件（运维须知）</h3>
 * <ul>
 *   <li>本服务在启动时从配置路径 {@code ${ip2region.xdb-path}}（默认
 *       {@code classpath:ip2region/ip2region.xdb}）加载整个 xdb 文件到内存，构建一个
 *       线程安全、可全局共享的 {@link Searcher} 实例。</li>
 *   <li><b>该二进制 xdb 文件不随源码提交</b>，需由运维从 ip2region 官方仓库
 *       （<a href="https://github.com/lionsoul2014/ip2region">lionsoul2014/ip2region</a>）
 *       下载最新的 {@code data/ip2region.xdb} 并放置到
 *       {@code zw-security/src/main/resources/ip2region/ip2region.xdb}，
 *       或通过 {@code ip2region.xdb-path} 指向外部文件路径（如
 *       {@code file:/opt/zwinsight/ip2region.xdb}）。详见同目录 {@code README.md}。</li>
 *   <li>若 xdb 文件缺失或加载失败，服务不会阻断启动，{@link #resolveLocation(String)}
 *       将返回 {@code null}（需求 9.4：解析失败不阻断登录流程），仅记录 WARN 日志。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLocationService {

    private final SysLoginDeviceMapper loginDeviceMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ResourceLoader resourceLoader;

    /** ip2region xdb 数据文件路径，支持 classpath: / file: 前缀。 */
    @Value("${ip2region.xdb-path:classpath:ip2region/ip2region.xdb}")
    private String xdbPath;

    /** 归属地解析失败/无数据时的占位文案。 */
    private static final String UNKNOWN_LOCATION = "未知";

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 全量缓存内存的 Searcher，线程安全，可跨线程共享；xdb 缺失时为 {@code null}。
     */
    private volatile Searcher searcher;

    /**
     * 启动时加载 xdb 到内存并构建共享 Searcher。
     * <p>失败时仅记录 WARN，不抛异常（避免阻断应用启动 / 登录流程）。</p>
     */
    @PostConstruct
    public void init() {
        try {
            Resource resource = resourceLoader.getResource(xdbPath);
            if (!resource.exists()) {
                log.warn("[LOGIN-LOCATION] ip2region xdb 文件不存在: {}，异地登录检测将不可用，"
                        + "请运维下载 ip2region.xdb 并放置到对应路径", xdbPath);
                return;
            }
            byte[] buffer;
            try (InputStream in = resource.getInputStream()) {
                buffer = in.readAllBytes();
            }
            this.searcher = Searcher.newWithBuffer(buffer);
            log.info("[LOGIN-LOCATION] ip2region xdb 加载成功: {}，大小={} bytes", xdbPath, buffer.length);
        } catch (Exception e) {
            // 加载失败不阻断启动；resolveLocation 将返回 null
            this.searcher = null;
            log.warn("[LOGIN-LOCATION] ip2region xdb 加载失败: {}，异地登录检测将不可用", xdbPath, e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (searcher != null) {
            try {
                searcher.close();
            } catch (Exception e) {
                log.warn("[LOGIN-LOCATION] 关闭 ip2region Searcher 失败", e);
            }
        }
    }

    /**
     * 解析 IP 归属地，返回 "省份|城市" 格式。
     *
     * <p>ip2region 查询结果格式为 "国家|区域|省份|城市|ISP"，本方法提取省份与城市拼接为
     * "省份|城市"。当某字段为空或为 ip2region 的占位符 "0" 时使用 "未知" 替代。</p>
     *
     * <p>需求 9.4：任何解析异常（xdb 未加载、IP 非法、查询出错）均记录 WARN 日志并返回
     * {@code null}，<b>不抛异常、不阻断登录流程</b>。</p>
     *
     * @param ip 登录 IP
     * @return "省份|城市"；解析失败返回 {@code null}
     */
    public String resolveLocation(String ip) {
        if (!StringUtils.hasText(ip)) {
            log.warn("[LOGIN-LOCATION] IP 为空，跳过归属地解析");
            return null;
        }
        Searcher s = this.searcher;
        if (s == null) {
            log.warn("[LOGIN-LOCATION] ip2region 未初始化，无法解析归属地: ip={}", ip);
            return null;
        }
        try {
            String region = s.search(ip.trim());
            if (!StringUtils.hasText(region)) {
                log.warn("[LOGIN-LOCATION] ip2region 返回空结果: ip={}", ip);
                return null;
            }
            // 国家|区域|省份|城市|ISP
            String[] parts = region.split("\\|", -1);
            String province = parts.length > 2 ? normalize(parts[2]) : UNKNOWN_LOCATION;
            String city = parts.length > 3 ? normalize(parts[3]) : UNKNOWN_LOCATION;
            return province + "|" + city;
        } catch (Exception e) {
            // 需求 9.4：解析失败仅记录日志，不阻断登录
            log.warn("[LOGIN-LOCATION] IP 归属地解析失败: ip={}", ip, e);
            return null;
        }
    }

    /**
     * 检测是否异地登录并在归属地变化时发送站内消息通知。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>解析当前登录 IP 的归属地；解析失败（返回 null）则直接返回，不通知（需求 9.4）。</li>
     *   <li>查询该用户在 {@code sys_login_device} 中最近一条带归属地的历史记录作为"上次登录地"。</li>
     *   <li>存在上次登录地且与本次不一致时，发布 {@link LoginLocationNotifyEvent}，
     *       由 message 模块发送站内消息（含登录时间、登录IP、归属地、设备信息，需求 9.3）。</li>
     * </ol>
     *
     * <p><b>调用时机：</b>应在 {@code DeviceManagerService.recordLogin} 写入本次设备记录
     * <em>之前</em>调用，以保证查询到的"最近记录"为上一次登录而非本次。</p>
     *
     * @param userId     用户ID
     * @param ip         本次登录 IP
     * @param deviceInfo 设备信息描述（如 "iPhone 15 Pro / iOS"）
     */
    public void detectAndNotify(Long userId, String ip, String deviceInfo) {
        if (userId == null) {
            return;
        }
        try {
            String currentLocation = resolveLocation(ip);
            if (currentLocation == null) {
                // 解析失败，不阻断、不通知
                return;
            }

            String lastLocation = findLastLocation(userId);
            // 首次登录（无历史记录）不通知；归属地一致不通知
            if (!StringUtils.hasText(lastLocation) || lastLocation.equals(currentLocation)) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            String title = "异地登录提醒";
            String content = String.format(
                    "您的账号于 %s 在新的归属地登录，请确认是否为本人操作。\n"
                            + "登录时间：%s\n登录IP：%s\n归属地：%s\n设备信息：%s",
                    now.format(TIME_FORMATTER),
                    now.format(TIME_FORMATTER),
                    ip,
                    currentLocation,
                    StringUtils.hasText(deviceInfo) ? deviceInfo : "未知设备");

            eventPublisher.publishEvent(new LoginLocationNotifyEvent(
                    this, userId, title, content, ip, currentLocation));

            log.info("[LOGIN-LOCATION] 检测到异地登录: userId={}, 上次={}, 本次={}",
                    userId, lastLocation, currentLocation);
        } catch (Exception e) {
            // 异地检测属于安全增强，任何异常都不应影响登录主流程
            log.warn("[LOGIN-LOCATION] 异地登录检测异常: userId={}, ip={}", userId, ip, e);
        }
    }

    /**
     * 查询用户最近一次登录的归属地（来自 sys_login_device 最新记录）。
     *
     * @return 最近归属地（"省份|城市"）；无历史记录返回 {@code null}
     */
    private String findLastLocation(Long userId) {
        List<SysLoginDevice> recent = loginDeviceMapper.selectList(
                new LambdaQueryWrapper<SysLoginDevice>()
                        .eq(SysLoginDevice::getUserId, userId)
                        .isNotNull(SysLoginDevice::getLocation)
                        .ne(SysLoginDevice::getLocation, "")
                        .orderByDesc(SysLoginDevice::getLoginTime)
                        .last("LIMIT 1"));
        if (recent == null || recent.isEmpty()) {
            return null;
        }
        return recent.get(0).getLocation();
    }

    /**
     * 归一化 ip2region 字段：空串或占位符 "0" 视为未知。
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value) || "0".equals(value.trim())) {
            return UNKNOWN_LOCATION;
        }
        return value.trim();
    }
}
