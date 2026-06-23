package com.zwinsight.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.message.domain.MsgMessage;
import com.zwinsight.message.mapper.MsgMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 站内消息服务
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MsgMessageMapper messageMapper;

    /**
     * 获取未读消息（分页）
     */
    public PageResult<MsgMessage> getUnreadMessages(Long userId, int page, int size) {
        Page<MsgMessage> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<MsgMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgMessage::getUserId, userId)
                .eq(MsgMessage::getIsRead, 0)
                .orderByDesc(MsgMessage::getCreatedAt);
        Page<MsgMessage> result = messageMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 获取所有消息（分页）
     */
    public PageResult<MsgMessage> getAllMessages(Long userId, int page, int size) {
        Page<MsgMessage> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<MsgMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgMessage::getUserId, userId)
                .orderByDesc(MsgMessage::getCreatedAt);
        Page<MsgMessage> result = messageMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 标记消息为已读
     */
    public void markAsRead(Long messageId) {
        LambdaUpdateWrapper<MsgMessage> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MsgMessage::getId, messageId)
                .set(MsgMessage::getIsRead, 1)
                .set(MsgMessage::getReadTime, LocalDateTime.now());
        messageMapper.update(null, wrapper);
    }

    /**
     * 标记全部已读
     */
    public void markAllAsRead(Long userId) {
        LambdaUpdateWrapper<MsgMessage> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MsgMessage::getUserId, userId)
                .eq(MsgMessage::getIsRead, 0)
                .set(MsgMessage::getIsRead, 1)
                .set(MsgMessage::getReadTime, LocalDateTime.now());
        messageMapper.update(null, wrapper);
    }

    /**
     * 发送消息
     */
    public void sendMessage(Long userId, String title, String content, String messageType, String businessType, Long businessId) {
        MsgMessage message = new MsgMessage();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setBusinessType(businessType);
        message.setBusinessId(businessId);
        message.setIsRead(0);
        messageMapper.insert(message);
    }

    /**
     * 获取未读消息数量
     */
    public long getUnreadCount(Long userId) {
        LambdaQueryWrapper<MsgMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgMessage::getUserId, userId)
                .eq(MsgMessage::getIsRead, 0);
        return messageMapper.selectCount(wrapper);
    }
}
