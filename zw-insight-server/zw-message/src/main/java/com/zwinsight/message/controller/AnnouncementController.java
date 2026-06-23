package com.zwinsight.message.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.message.domain.MsgAnnouncement;
import com.zwinsight.message.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 公告接口
 */
@RestController
@RequestMapping("/api/v1/message/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public R<PageResult<MsgAnnouncement>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status) {
        return R.ok(announcementService.page(page, size, title, status));
    }

    @GetMapping("/{id}")
    public R<MsgAnnouncement> getById(@PathVariable Long id) {
        return R.ok(announcementService.getById(id));
    }

    @PostMapping
    public R<Void> save(@RequestBody MsgAnnouncement announcement) {
        announcementService.save(announcement);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody MsgAnnouncement announcement) {
        announcement.setId(id);
        announcementService.update(announcement);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        announcementService.publish(id);
        return R.ok();
    }

    @PostMapping("/{id}/revoke")
    public R<Void> revoke(@PathVariable Long id) {
        announcementService.revoke(id);
        return R.ok();
    }
}
