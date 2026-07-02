package com.zwinsight.message.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.message.domain.MsgMessage;
import com.zwinsight.message.mapper.MsgMessageMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MsgMessage.class);
    }

    @Mock private MsgMessageMapper messageMapper;

    @InjectMocks
    private MessageService messageService;

    @Test
    @DisplayName("发送消息：正常插入")
    void testSendMessage() {
        when(messageMapper.insert(any(MsgMessage.class))).thenReturn(1);

        messageService.sendMessage(1L, "标题", "内容", "SYSTEM", "APPROVAL", 100L);

        verify(messageMapper).insert(argThat(msg ->
                msg.getUserId() == 1L && "标题".equals(msg.getTitle()) && msg.getIsRead() == 0));
    }

    @Test
    @DisplayName("获取未读数量：返回计数")
    void testGetUnreadCount() {
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        long count = messageService.getUnreadCount(1L);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("标记已读：更新isRead和readTime")
    void testMarkAsRead() {
        messageService.markAsRead(1L);

        verify(messageMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("标记全部已读：按用户批量更新")
    void testMarkAllAsRead() {
        messageService.markAllAsRead(1L);

        verify(messageMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }
}
