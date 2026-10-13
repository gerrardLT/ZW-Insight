package com.zwinsight.message.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.message.domain.MsgNotice;
import com.zwinsight.message.mapper.MsgNoticeMapper;
import com.zwinsight.security.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MsgNotice.class);
    }

    @Mock private MsgNoticeMapper noticeMapper;
    @Mock private MessageService messageService;
    @Mock private SysUserMapper sysUserMapper;

    @InjectMocks
    private NoticeService noticeService;

    @Test
    @DisplayName("分页查询：返回 PageResult 结构")
    void testPage_returnsPageResult() {
        Page<MsgNotice> page = new Page<>(1, 10);
        page.setRecords(List.of(new MsgNotice()));
        page.setTotal(1);
        when(noticeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<MsgNotice> result = noticeService.page(1, 10, "放假", "PUBLISHED");

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(noticeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> noticeService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("通知不存在");
    }

    @Test
    @DisplayName("新增通知：初始状态置为 DRAFT")
    void testSave_setsDraftStatus() {
        MsgNotice notice = new MsgNotice();
        notice.setTitle("放假通知");

        noticeService.save(notice);

        ArgumentCaptor<MsgNotice> captor = ArgumentCaptor.forClass(MsgNotice.class);
        verify(noticeMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("发布通知：状态置 PUBLISHED 并按指定接收人生成站内消息")
    void testPublish_generatesMessagesForTargets() {
        MsgNotice notice = new MsgNotice();
        notice.setId(1L);
        notice.setStatus("DRAFT");
        notice.setTitle("放假通知");
        notice.setContent("本周五放假");
        notice.setTargetUserIds("11,22");
        when(noticeMapper.selectById(1L)).thenReturn(notice);

        noticeService.publish(1L);

        verify(noticeMapper).updateById(notice);
        assertThat(notice.getStatus()).isEqualTo("PUBLISHED");
        verify(messageService, times(2)).sendMessage(anyLong(), eq("放假通知"), eq("本周五放假"),
                eq("SYSTEM"), eq("NOTICE"), eq(1L));
    }

    @Test
    @DisplayName("发布通知：未指定接收人时广播本租户全部启用用户")
    void testPublish_noTargets_broadcastsToTenant() {
        MsgNotice notice = new MsgNotice();
        notice.setId(2L);
        notice.setStatus("DRAFT");
        notice.setTitle("系统维护");
        notice.setContent("今晚维护");
        when(noticeMapper.selectById(2L)).thenReturn(notice);
        when(sysUserMapper.selectActiveUserIds(9999L)).thenReturn(List.of(7L, 8L, 9L));

        try (MockedStatic<SecurityContextHolder> ctx = mockStatic(SecurityContextHolder.class)) {
            ctx.when(SecurityContextHolder::getTenantId).thenReturn(9999L);
            noticeService.publish(2L);
        }

        verify(messageService, times(3)).sendMessage(anyLong(), anyString(), anyString(),
                eq("SYSTEM"), eq("NOTICE"), eq(2L));
    }

    @Test
    @DisplayName("发布通知：已发布的通知拒绝重复发布（防重复推送）")
    void testPublish_alreadyPublished_rejected() {
        MsgNotice notice = new MsgNotice();
        notice.setId(3L);
        notice.setStatus("PUBLISHED");
        when(noticeMapper.selectById(3L)).thenReturn(notice);

        assertThatThrownBy(() -> noticeService.publish(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请勿重复发布");

        verify(messageService, never()).sendMessage(anyLong(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("发布通知：不存在抛异常")
    void testPublish_notFound() {
        when(noticeMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> noticeService.publish(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("通知不存在");
    }
}
