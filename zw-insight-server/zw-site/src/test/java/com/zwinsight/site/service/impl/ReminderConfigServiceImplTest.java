package com.zwinsight.site.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.site.domain.BizReminderConfig;
import com.zwinsight.site.dto.ReminderConfigUpdateRequest;
import com.zwinsight.site.mapper.BizReminderConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReminderConfigServiceImpl 单元测试（2026-08-15 P3 后端低覆盖补测）
 *
 * 覆盖场景:
 * - getConfig 配置不存在返回默认值（3/7/30/enabled）
 * - getConfig 配置存在返回 DB 值
 * - updateConfig escalationDays >= longOverdueDays 抛 BusinessException
 * - updateConfig 配置不存在走 insert
 * - updateConfig 配置存在走 updateById
 */
@ExtendWith(MockitoExtension.class)
class ReminderConfigServiceImplTest {

    @Mock
    private BizReminderConfigMapper reminderConfigMapper;

    @InjectMocks
    private ReminderConfigServiceImpl reminderConfigService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizReminderConfig.class);
    }

    private ReminderConfigUpdateRequest request(int interval, int escalation, int longOverdue, boolean enabled) {
        ReminderConfigUpdateRequest req = new ReminderConfigUpdateRequest();
        req.setIntervalDays(interval);
        req.setEscalationDays(escalation);
        req.setLongOverdueDays(longOverdue);
        req.setEnabled(enabled);
        return req;
    }

    @Test
    @DisplayName("getConfig 配置不存在返回默认值")
    void getConfig_notExists_returnsDefault() {
        when(reminderConfigMapper.selectOne(any())).thenReturn(null);

        BizReminderConfig config = reminderConfigService.getConfig(1L);

        assertThat(config.getTenantId()).isEqualTo(1L);
        assertThat(config.getIntervalDays()).isEqualTo(3);
        assertThat(config.getEscalationDays()).isEqualTo(7);
        assertThat(config.getLongOverdueDays()).isEqualTo(30);
        assertThat(config.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("getConfig 配置存在返回 DB 值")
    void getConfig_exists_returnsDbValue() {
        BizReminderConfig existing = new BizReminderConfig();
        existing.setTenantId(2L);
        existing.setIntervalDays(5);
        existing.setEscalationDays(10);
        existing.setLongOverdueDays(60);
        existing.setEnabled(false);
        when(reminderConfigMapper.selectOne(any())).thenReturn(existing);

        BizReminderConfig config = reminderConfigService.getConfig(2L);

        assertThat(config.getIntervalDays()).isEqualTo(5);
        assertThat(config.getEscalationDays()).isEqualTo(10);
        assertThat(config.getLongOverdueDays()).isEqualTo(60);
        assertThat(config.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("updateConfig 升级阈值 >= 长期超期天数抛异常")
    void updateConfig_invalidThreshold_throws() {
        ReminderConfigUpdateRequest req = request(3, 30, 30, true);

        assertThatThrownBy(() -> reminderConfigService.updateConfig(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("升级通知阈值天数必须小于长期超期停止催办天数");
        verify(reminderConfigMapper, never()).insert(any());
        verify(reminderConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateConfig 配置不存在走 insert")
    void updateConfig_notExists_inserts() {
        when(reminderConfigMapper.selectOne(any())).thenReturn(null);
        ReminderConfigUpdateRequest req = request(4, 8, 40, true);

        reminderConfigService.updateConfig(3L, req);

        verify(reminderConfigMapper).insert(any(BizReminderConfig.class));
        verify(reminderConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateConfig 配置存在走 updateById")
    void updateConfig_exists_updates() {
        BizReminderConfig existing = new BizReminderConfig();
        existing.setId(99L);
        existing.setTenantId(4L);
        when(reminderConfigMapper.selectOne(any())).thenReturn(existing);
        ReminderConfigUpdateRequest req = request(2, 6, 20, false);

        reminderConfigService.updateConfig(4L, req);

        verify(reminderConfigMapper).updateById(existing);
        verify(reminderConfigMapper, never()).insert(any());
        assertThat(existing.getIntervalDays()).isEqualTo(2);
        assertThat(existing.getEscalationDays()).isEqualTo(6);
        assertThat(existing.getLongOverdueDays()).isEqualTo(20);
        assertThat(existing.getEnabled()).isFalse();
    }
}
