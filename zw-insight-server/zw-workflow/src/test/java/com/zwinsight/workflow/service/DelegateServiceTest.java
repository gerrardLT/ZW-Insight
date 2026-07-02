package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfDelegateConfig;
import com.zwinsight.workflow.mapper.WfDelegateConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DelegateService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DelegateServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                WfDelegateConfig.class);
    }

    @Mock
    private WfDelegateConfigMapper delegateConfigMapper;

    @InjectMocks
    private DelegateService delegateService;

    @Test
    @DisplayName("创建委托：代理人不能为空")
    void testCreateDelegation_nullDelegateId_throws() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(1L);

            assertThatThrownBy(() -> delegateService.createDelegation(
                    null, LocalDateTime.now(), LocalDateTime.now().plusDays(1), "休假"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("代理人不能为空");
        }
    }

    @Test
    @DisplayName("创建委托：不能委托给自己")
    void testCreateDelegation_selfDelegate_throws() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);

            assertThatThrownBy(() -> delegateService.createDelegation(
                    100L, LocalDateTime.now(), LocalDateTime.now().plusDays(1), "test"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不能委托给自己");
        }
    }

    @Test
    @DisplayName("创建委托：结束时间早于开始时间抛异常")
    void testCreateDelegation_endBeforeStart_throws() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);

            LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2026, 7, 1, 0, 0);

            assertThatThrownBy(() -> delegateService.createDelegation(200L, start, end, "test"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("结束时间不能早于开始时间");
        }
    }

    @Test
    @DisplayName("创建委托：已有生效委托时拒绝")
    void testCreateDelegation_existingActive_throws() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);

            LocalDateTime start = LocalDateTime.now().plusHours(1);
            LocalDateTime end = LocalDateTime.now().plusDays(7);

            WfDelegateConfig existing = new WfDelegateConfig();
            existing.setStatus("ACTIVE");
            when(delegateConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            assertThatThrownBy(() -> delegateService.createDelegation(200L, start, end, "test"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已有生效中的委托配置");
        }
    }

    @Test
    @DisplayName("创建委托：正常创建成功")
    void testCreateDelegation_success() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);

            LocalDateTime start = LocalDateTime.now().plusHours(1);
            LocalDateTime end = LocalDateTime.now().plusDays(7);

            when(delegateConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(delegateConfigMapper.insert(any(WfDelegateConfig.class))).thenAnswer(inv -> {
                WfDelegateConfig c = inv.getArgument(0);
                c.setId(1L);
                return 1;
            });

            Long id = delegateService.createDelegation(200L, start, end, "年假");

            assertThat(id).isEqualTo(1L);
            verify(delegateConfigMapper).insert(argThat(c ->
                    c.getDelegatorId().equals(100L)
                            && c.getDelegateId().equals(200L)
                            && "ACTIVE".equals(c.getStatus())
                            && "年假".equals(c.getReason())));
        }
    }

    @Test
    @DisplayName("取消委托：非本人操作抛异常")
    void testCancelDelegation_notOwner_throws() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(999L);

            WfDelegateConfig config = new WfDelegateConfig();
            config.setId(1L);
            config.setDelegatorId(100L);
            config.setStatus("ACTIVE");
            when(delegateConfigMapper.selectById(1L)).thenReturn(config);

            assertThatThrownBy(() -> delegateService.cancelDelegation(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("只能取消自己的委托配置");
        }
    }

    @Test
    @DisplayName("取消委托：非生效状态抛异常")
    void testCancelDelegation_notActive_throws() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);

            WfDelegateConfig config = new WfDelegateConfig();
            config.setId(1L);
            config.setDelegatorId(100L);
            config.setStatus("CANCELLED");
            when(delegateConfigMapper.selectById(1L)).thenReturn(config);

            assertThatThrownBy(() -> delegateService.cancelDelegation(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已不是生效状态");
        }
    }

    @Test
    @DisplayName("取消委托：正常取消")
    void testCancelDelegation_success() {
        try (var sc = mockStatic(SecurityContextHolder.class)) {
            sc.when(SecurityContextHolder::getUserId).thenReturn(100L);

            WfDelegateConfig config = new WfDelegateConfig();
            config.setId(1L);
            config.setDelegatorId(100L);
            config.setStatus("ACTIVE");
            when(delegateConfigMapper.selectById(1L)).thenReturn(config);

            delegateService.cancelDelegation(1L);

            verify(delegateConfigMapper).updateById(argThat(c -> "CANCELLED".equals(c.getStatus())));
        }
    }

    @Test
    @DisplayName("查找代理人：有生效委托返回代理人ID")
    void testFindDelegateUser_found() {
        WfDelegateConfig config = new WfDelegateConfig();
        config.setDelegateId(200L);
        when(delegateConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        Long result = delegateService.findDelegateUser(100L);

        assertThat(result).isEqualTo(200L);
    }

    @Test
    @DisplayName("查找代理人：无委托返回 null")
    void testFindDelegateUser_notFound() {
        when(delegateConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Long result = delegateService.findDelegateUser(100L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("清理过期委托：返回清理数量")
    void testExpireOverdueDelegations() {
        when(delegateConfigMapper.update(isNull(), any())).thenReturn(3);

        int count = delegateService.expireOverdueDelegations();

        assertThat(count).isEqualTo(3);
    }
}
