package com.zwinsight.security.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.domain.SysLoginDevice;
import com.zwinsight.security.dto.DeviceInfo;
import com.zwinsight.security.dto.LoginDeviceVO;
import com.zwinsight.security.mapper.SysLoginDeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DeviceManagerService 单元测试（阶段四批 1 补测）
 * <p>登录设备记录/查询/远程注销/最大设备数自动淘汰/Token 黑名单。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceManagerService — 登录设备管理")
class DeviceManagerServiceTest {

    @Mock
    private SysLoginDeviceMapper loginDeviceMapper;

    @Mock
    private RedisUtils redisUtils;

    @InjectMocks
    private DeviceManagerService service;

    @BeforeEach
    void setUp() {
        // @Value 字段在单测中不注入，手动设置默认配置值
        ReflectionTestUtils.setField(service, "maxDevices", 5);
    }

    private SysLoginDevice device(Long id, Long userId, String token, LocalDateTime loginTime) {
        SysLoginDevice d = new SysLoginDevice();
        d.setId(id);
        d.setUserId(userId);
        d.setToken(token);
        d.setLoginTime(loginTime);
        d.setStatus(1);
        return d;
    }

    private String sha256(String input) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    @DisplayName("recordLogin - 落库活跃设备并携带设备/IP/归属地")
    void recordLogin_success() {
        DeviceInfo info = new DeviceInfo();
        info.setDeviceId("dev-1");
        info.setDeviceName("Chrome");
        info.setOs("Windows");
        when(loginDeviceMapper.selectList(any())).thenReturn(List.of());

        service.recordLogin(1L, info, "tk-1", "1.2.3.4", "浙江省|杭州市");

        ArgumentCaptor<SysLoginDevice> captor = ArgumentCaptor.forClass(SysLoginDevice.class);
        verify(loginDeviceMapper).insert(captor.capture());
        SysLoginDevice saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getDeviceId()).isEqualTo("dev-1");
        assertThat(saved.getToken()).isEqualTo("tk-1");
        assertThat(saved.getIpAddress()).isEqualTo("1.2.3.4");
        assertThat(saved.getLocation()).isEqualTo("浙江省|杭州市");
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordLogin - userId 为空或 token 空白拒绝")
    void recordLogin_invalidParams() {
        assertThatThrownBy(() -> service.recordLogin(null, null, "tk", "1.1.1.1", null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("用户ID不能为空");
        assertThatThrownBy(() -> service.recordLogin(1L, null, "  ", "1.1.1.1", null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("登录Token不能为空");
        verify(loginDeviceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("listDevices - 当前 token 对应设备标记 isCurrent")
    void listDevices_marksCurrent() {
        when(loginDeviceMapper.selectList(any())).thenReturn(List.of(
                device(1L, 1L, "tk-a", LocalDateTime.now()),
                device(2L, 1L, "tk-b", LocalDateTime.now())));

        List<LoginDeviceVO> vos = service.listDevices(1L, "tk-b");

        assertThat(vos).hasSize(2);
        assertThat(vos.get(0).getIsCurrent()).isFalse();
        assertThat(vos.get(1).getIsCurrent()).isTrue();
    }

    @Test
    @DisplayName("revokeDevice - 注销置 REVOKED 且 token 入黑名单并删除会话")
    void revokeDevice_success_blacklistsToken() throws Exception {
        SysLoginDevice d = device(1L, 1L, "tk-old", LocalDateTime.now());
        when(loginDeviceMapper.selectById(1L)).thenReturn(d);
        when(redisUtils.getExpire("token:tk-old", TimeUnit.SECONDS)).thenReturn(600L);

        service.revokeDevice(1L, 1L, "tk-current");

        assertThat(d.getStatus()).isEqualTo(0);
        verify(loginDeviceMapper).updateById(d);
        verify(redisUtils).set(eq("token:blacklist:" + sha256("tk-old")), eq("1"),
                eq(600L), eq(TimeUnit.SECONDS));
        verify(redisUtils).delete("token:tk-old");
    }

    @Test
    @DisplayName("revokeDevice - 设备不存在或不属于该用户返回 404")
    void revokeDevice_notFoundOrOtherUser() {
        when(loginDeviceMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.revokeDevice(1L, 1L, "tk"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("设备不存在");

        when(loginDeviceMapper.selectById(2L)).thenReturn(device(2L, 999L, "tk-x", LocalDateTime.now()));
        assertThatThrownBy(() -> service.revokeDevice(1L, 2L, "tk"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("设备不存在");
    }

    @Test
    @DisplayName("revokeDevice - 禁止注销当前正在使用的设备")
    void revokeDevice_currentDeviceForbidden() {
        when(loginDeviceMapper.selectById(1L)).thenReturn(device(1L, 1L, "tk-same", LocalDateTime.now()));

        assertThatThrownBy(() -> service.revokeDevice(1L, 1L, "tk-same"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能注销当前使用的设备");
        verify(loginDeviceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("autoEvictOldest - 超出上限按登录时间升序淘汰最早设备")
    void autoEvictOldest_evictsOldest() throws Exception {
        SysLoginDevice oldest = device(1L, 1L, "tk-oldest", LocalDateTime.of(2026, 8, 1, 1, 0));
        List<SysLoginDevice> actives = List.of(
                oldest,
                device(2L, 1L, "tk-2", LocalDateTime.of(2026, 8, 2, 1, 0)),
                device(3L, 1L, "tk-3", LocalDateTime.of(2026, 8, 3, 1, 0)));
        when(loginDeviceMapper.selectList(any())).thenReturn(actives);
        when(redisUtils.getExpire(anyString(), any(TimeUnit.class))).thenReturn(100L);

        service.autoEvictOldest(1L, 2);

        assertThat(oldest.getStatus()).isEqualTo(0);
        verify(loginDeviceMapper).updateById(oldest);
        verify(redisUtils).set(eq("token:blacklist:" + sha256("tk-oldest")), eq("1"),
                eq(100L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("autoEvictOldest - 未超限或 max<=0 不淘汰")
    void autoEvictOldest_noEviction() {
        when(loginDeviceMapper.selectList(any())).thenReturn(List.of(
                device(1L, 1L, "tk-1", LocalDateTime.now())));

        service.autoEvictOldest(1L, 5);
        verify(loginDeviceMapper, never()).updateById(any());

        service.autoEvictOldest(1L, 0);
        verify(loginDeviceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("addToBlacklist - 会话 key 无 TTL 时使用 24h 兜底")
    void addToBlacklist_ttlFallback() {
        when(redisUtils.getExpire("token:tk-x", TimeUnit.SECONDS)).thenReturn(null);

        service.addToBlacklist("tk-x");

        verify(redisUtils).set(anyString(), eq("1"), eq(24 * 60 * 60L), eq(TimeUnit.SECONDS));
        verify(redisUtils).delete("token:tk-x");
    }

    @Test
    @DisplayName("addToBlacklist - 空 token 不触碰 Redis")
    void addToBlacklist_blankToken_skipped() {
        service.addToBlacklist("  ");
        service.addToBlacklist(null);
        verifyNoInteractions(redisUtils);
    }
}
