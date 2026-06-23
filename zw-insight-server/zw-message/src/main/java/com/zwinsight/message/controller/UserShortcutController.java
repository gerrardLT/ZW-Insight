package com.zwinsight.message.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.R;
import com.zwinsight.message.domain.MsgUserShortcut;
import com.zwinsight.message.service.UserShortcutService;
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

    @GetMapping
    public R<List<MsgUserShortcut>> list() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(userShortcutService.getByUserId(userId));
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
