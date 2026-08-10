package com.zwinsight.workflow.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfUrgeConfig;
import com.zwinsight.workflow.mapper.WfUrgeConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UrgeConfigService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class UrgeConfigServiceTest {

    @Mock private WfUrgeConfigMapper urgeConfigMapper;

    @InjectMocks
    private UrgeConfigService urgeConfigService;

    @Test
    @DisplayName("获取配置：无记录时返回默认值")
    void testGetConfig_noRecord_returnsDefault() {
        when(urgeConfigMapper.selectOne(any())).thenReturn(null);

        WfUrgeConfig config = urgeConfigService.getConfig();

        assertThat(config.getTimeoutHours()).isEqualTo(24);
        assertThat(config.getIntervalHours()).isEqualTo(4);
        assertThat(config.getMaxUrgeCount()).isEqualTo(3);
        assertThat(config.getAutoUrgeEnabled()).isEqualTo(1);
    }

    @Test
    @DisplayName("获取配置：有记录时直接返回")
    void testGetConfig_hasRecord_returnsIt() {
        WfUrgeConfig existing = new WfUrgeConfig();
        existing.setTimeoutHours(48);
        when(urgeConfigMapper.selectOne(any())).thenReturn(existing);

        WfUrgeConfig config = urgeConfigService.getConfig();

        assertThat(config.getTimeoutHours()).isEqualTo(48);
    }

    @Test
    @DisplayName("保存配置：超时时间小于1拒绝")
    void testSaveConfig_timeoutTooSmall_rejected() {
        WfUrgeConfig config = new WfUrgeConfig();
        config.setTimeoutHours(0);

        assertThatThrownBy(() -> urgeConfigService.saveConfig(config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超时时间不能小于1小时");
    }

    @Test
    @DisplayName("保存配置：催办间隔小于1拒绝")
    void testSaveConfig_intervalTooSmall_rejected() {
        WfUrgeConfig config = new WfUrgeConfig();
        config.setTimeoutHours(24);
        config.setIntervalHours(0);

        assertThatThrownBy(() -> urgeConfigService.saveConfig(config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("催办间隔不能小于1小时");
    }

    @Test
    @DisplayName("保存配置：最大催办次数小于1拒绝")
    void testSaveConfig_maxCountTooSmall_rejected() {
        WfUrgeConfig config = new WfUrgeConfig();
        config.setTimeoutHours(24);
        config.setIntervalHours(4);
        config.setMaxUrgeCount(0);

        assertThatThrownBy(() -> urgeConfigService.saveConfig(config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最大催办次数不能小于1次");
    }

    @Test
    @DisplayName("保存配置：已有配置走更新")
    void testSaveConfig_existing_updates() {
        WfUrgeConfig existing = new WfUrgeConfig();
        existing.setId(1L);
        when(urgeConfigMapper.selectOne(any())).thenReturn(existing);

        WfUrgeConfig config = new WfUrgeConfig();
        config.setTimeoutHours(30);
        config.setIntervalHours(6);
        config.setMaxUrgeCount(5);
        config.setAutoUrgeEnabled(1);

        urgeConfigService.saveConfig(config);

        verify(urgeConfigMapper).updateById(existing);
        assertThat(existing.getTimeoutHours()).isEqualTo(30);
        verify(urgeConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("保存配置：无已有配置走新建")
    void testSaveConfig_none_inserts() {
        when(urgeConfigMapper.selectOne(any())).thenReturn(null);

        WfUrgeConfig config = new WfUrgeConfig();
        config.setTimeoutHours(24);
        config.setIntervalHours(4);
        config.setMaxUrgeCount(3);
        config.setAutoUrgeEnabled(1);

        urgeConfigService.saveConfig(config);

        verify(urgeConfigMapper).insert(config);
        verify(urgeConfigMapper, never()).updateById(any());
    }
}
