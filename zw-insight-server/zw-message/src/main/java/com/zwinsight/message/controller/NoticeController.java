package com.zwinsight.message.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.message.domain.MsgNotice;
import com.zwinsight.message.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 通知接口
 */
@RestController
@RequestMapping("/api/v1/message/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public R<PageResult<MsgNotice>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String title) {
        return R.ok(noticeService.page(page, size, title));
    }

    @GetMapping("/{id}")
    public R<MsgNotice> getById(@PathVariable Long id) {
        return R.ok(noticeService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody MsgNotice notice) {
        noticeService.save(notice);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        noticeService.publish(id);
        return R.ok();
    }
}
