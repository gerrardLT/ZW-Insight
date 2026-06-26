package com.zwinsight.message.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.message.domain.MsgAvailableShortcut;
import com.zwinsight.message.domain.MsgUserShortcut;
import com.zwinsight.message.dto.ShortcutBatchSaveRequest;
import com.zwinsight.message.dto.ShortcutBatchSaveResponse;
import com.zwinsight.message.service.UserShortcutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户快捷入口接口
 */
@RestController
@RequestMapping("/api/v1/message/shortcut")
@RequiredArgsConstructor
public class UserShortcutController {

    private final UserShortcutService userShortcutService;

    /**
     * 获取当前用户已选快捷入口配置（未配置时返回默认列表）
     */
    @GetMapping
    public R<List<MsgUserShortcut>> list() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(userShortcutService.getUserConfig(userId));
    }

    /**
     * 获取全部可选功能列表
     */
    @GetMapping("/available")
    public R<List<MsgAvailableShortcut>> available() {
        return R.ok(userShortcutService.getAvailableList());
    }

    /**
     * 批量保存用户快捷入口配置（整体替换）
     */
    @PostMapping("/batch")
    public R<ShortcutBatchSaveResponse> batchSave(@Valid @RequestBody ShortcutBatchSaveRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(userShortcutService.batchSave(userId, request.getShortcutIds()));
    }

    @PostMapping
    public R<Void> save(@RequestBody MsgUserShortcut shortcut) {
        Long userId = SecurityContextHolder.getUserId();
        shortcut.setUserId(userId);
        userShortcutService.save(shortcut);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userShortcutService.delete(id);
        return R.ok();
    }

    @PutMapping("/sort")
    public R<Void> updateSort(@RequestBody List<MsgUserShortcut> shortcuts) {
        userShortcutService.updateSort(shortcuts);
        return R.ok();
    }
}
