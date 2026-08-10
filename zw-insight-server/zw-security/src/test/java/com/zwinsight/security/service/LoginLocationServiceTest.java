package com.zwinsight.security.service;

import com.zwinsight.common.event.LoginLocationNotifyEvent;
import com.zwinsight.security.domain.SysLoginDevice;
import com.zwinsight.security.mapper.SysLoginDeviceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lionsoul.ip2region.xdb.Searcher;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * LoginLocationService 单元测试（阶段四批 1 补测）
 * <p>异地登录检测：归属地解析容错（需求 9.4 不阻断登录）、变化时发事件、一致/首登不通知。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginLocationService — 异地登录检测")
class LoginLocationServiceTest {

    @Mock
    private SysLoginDeviceMapper loginDeviceMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ResourceLoader resourceLoader;

    @InjectMocks
    private LoginLocationService service;

    private void injectSearcher(Searcher searcher) {
        ReflectionTestUtils.setField(service, "searcher", searcher);
    }

    @Test
    @DisplayName("resolveLocation - IP 为空或 searcher 未加载返回 null 不抛异常")
    void resolveLocation_unavailable_returnsNull() {
        assertThat(service.resolveLocation(null)).isNull();
        assertThat(service.resolveLocation("  ")).isNull();
        assertThat(service.resolveLocation("1.2.3.4")).isNull(); // searcher 未初始化
    }

    @Test
    @DisplayName("resolveLocation - 正常解析提取 省份|城市，占位符 0 归一化为未知")
    void resolveLocation_parsesRegion() throws Exception {
        Searcher searcher = mock(Searcher.class);
        when(searcher.search("1.2.3.4")).thenReturn("中国|0|浙江省|杭州市|电信");
        injectSearcher(searcher);

        assertThat(service.resolveLocation(" 1.2.3.4 ")).isEqualTo("浙江省|杭州市");

        when(searcher.search("9.9.9.9")).thenReturn("中国|0|0|0|电信");
        assertThat(service.resolveLocation("9.9.9.9")).isEqualTo("未知|未知");
    }

    @Test
    @DisplayName("resolveLocation - 查询异常返回 null（需求 9.4 不阻断登录）")
    void resolveLocation_searchThrows_returnsNull() throws Exception {
        Searcher searcher = mock(Searcher.class);
        when(searcher.search(any())).thenThrow(new RuntimeException("xdb corrupt"));
        injectSearcher(searcher);

        assertThat(service.resolveLocation("1.2.3.4")).isNull();
    }

    @Test
    @DisplayName("detectAndNotify - 归属地变化发布通知事件（含 IP 与归属地）")
    void detectAndNotify_locationChanged_publishesEvent() throws Exception {
        Searcher searcher = mock(Searcher.class);
        when(searcher.search("1.2.3.4")).thenReturn("中国|0|浙江省|杭州市|电信");
        injectSearcher(searcher);

        SysLoginDevice last = new SysLoginDevice();
        last.setLocation("北京市|北京市");
        when(loginDeviceMapper.selectList(any())).thenReturn(List.of(last));

        service.detectAndNotify(1L, "1.2.3.4", "Chrome / Windows");

        ArgumentCaptor<LoginLocationNotifyEvent> captor =
                ArgumentCaptor.forClass(LoginLocationNotifyEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        LoginLocationNotifyEvent event = captor.getValue();
        assertThat(event.getTargetUserId()).isEqualTo(1L);
        assertThat(event.getContent()).contains("浙江省|杭州市").contains("1.2.3.4");
    }

    @Test
    @DisplayName("detectAndNotify - 首次登录（无历史）或归属地一致不通知")
    void detectAndNotify_noChange_noEvent() throws Exception {
        Searcher searcher = mock(Searcher.class);
        when(searcher.search("1.2.3.4")).thenReturn("中国|0|浙江省|杭州市|电信");
        injectSearcher(searcher);

        when(loginDeviceMapper.selectList(any())).thenReturn(Collections.emptyList());
        service.detectAndNotify(1L, "1.2.3.4", null);

        SysLoginDevice same = new SysLoginDevice();
        same.setLocation("浙江省|杭州市");
        when(loginDeviceMapper.selectList(any())).thenReturn(List.of(same));
        service.detectAndNotify(1L, "1.2.3.4", null);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("detectAndNotify - userId 为 null 或解析失败均不通知不查库")
    void detectAndNotify_skipCases() {
        service.detectAndNotify(null, "1.2.3.4", null);
        verifyNoInteractions(loginDeviceMapper, eventPublisher);

        service.detectAndNotify(1L, "1.2.3.4", null); // searcher 未加载 → 解析 null → 直接返回
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("init - xdb 文件缺失不抛异常（searcher 保持 null）；destroy 幂等安全")
    void init_resourceMissing_graceful() {
        ReflectionTestUtils.setField(service, "xdbPath", "classpath:ip2region/not-exist.xdb");
        Resource missing = mock(Resource.class);
        when(missing.exists()).thenReturn(false);
        when(resourceLoader.getResource("classpath:ip2region/not-exist.xdb")).thenReturn(missing);

        assertThatCode(() -> {
            service.init();
            service.destroy();
        }).doesNotThrowAnyException();
        assertThat(service.resolveLocation("1.2.3.4")).isNull();
    }
}
