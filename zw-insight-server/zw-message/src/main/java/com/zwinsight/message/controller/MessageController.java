package com.zwinsight.message.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.message.domain.MsgMessage;
import com.zwinsight.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 站内消息接口
 * <p>权限豁免说明：本 Controller 全部端点按 {@code SecurityContextHolder.getUserId()}
 * 隔离个人数据，不存在越权读取面；且移动端/各角色消息铃铛普遍依赖，
 * 故不加 {@code @RequiresPermission}（与 UserShortcutController 同策略）。</p>
 */
@RestController
@RequestMapping("/api/v1/message/msg")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/unread")
    public R<PageResult<MsgMessage>> unread(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(messageService.getUnreadMessages(userId, page, size));
    }

    @GetMapping("/all")
    public R<PageResult<MsgMessage>> all(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(messageService.getAllMessages(userId, page, size));
    }

    @PutMapping("/{id}/read")
    public R<Void> markAsRead(@PathVariable Long id) {
        Long userId = SecurityContextHolder.getUserId();
        messageService.markAsRead(id, userId);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Void> markAllAsRead() {
        Long userId = SecurityContextHolder.getUserId();
        messageService.markAllAsRead(userId);
        return R.ok();
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(messageService.getUnreadCount(userId));
    }
}
