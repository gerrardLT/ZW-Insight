package com.zwinsight.message.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.message.domain.MsgAnnouncement;
import com.zwinsight.message.mapper.MsgAnnouncementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 公告服务
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final MsgAnnouncementMapper announcementMapper;

    /**
     * 分页查询公告
     */
    public PageResult<MsgAnnouncement> page(int page, int size, String title, String status) {
        Page<MsgAnnouncement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<MsgAnnouncement> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(title), MsgAnnouncement::getTitle, title)
                .eq(StrUtil.isNotBlank(status), MsgAnnouncement::getStatus, status)
                .orderByDesc(MsgAnnouncement::getIsTop)
                .orderByDesc(MsgAnnouncement::getCreatedAt);
        Page<MsgAnnouncement> result = announcementMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public MsgAnnouncement getById(Long id) {
        MsgAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        return announcement;
    }

    /**
     * 新增公告
     */
    public void save(MsgAnnouncement announcement) {
        announcement.setStatus("DRAFT");
        announcementMapper.insert(announcement);
    }

    /**
     * 更新公告（P2 修复：仅草稿/已撤回可编辑；status/publishTime 剥离防绕过发布状态机）
     */
    public void update(MsgAnnouncement announcement) {
        MsgAnnouncement existing = announcementMapper.selectById(announcement.getId());
        if (existing == null) {
            throw new BusinessException("公告不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !"REVOKED".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿/已撤回公告可编辑");
        }
        announcement.setStatus(null);
        announcement.setPublishTime(null);
        announcementMapper.updateById(announcement);
    }

    /**
     * 删除公告（P2 修复：仅草稿/已撤回可删，已发布公告须先撤回）
     */
    public void delete(Long id) {
        MsgAnnouncement existing = announcementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("公告不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !"REVOKED".equals(existing.getStatus())) {
            throw new BusinessException("已发布公告不可直接删除，请先撤回");
        }
        announcementMapper.deleteById(id);
    }

    /**
     * 发布公告（P2 修复：仅草稿/已撤回可发布，防重复发布覆盖发布时间）
     */
    public void publish(Long id) {
        MsgAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        if (!"DRAFT".equals(announcement.getStatus()) && !"REVOKED".equals(announcement.getStatus())) {
            throw new BusinessException("仅草稿/已撤回公告可发布");
        }
        announcement.setStatus("PUBLISHED");
        announcement.setPublishTime(LocalDateTime.now());
        announcementMapper.updateById(announcement);
    }

    /**
     * 撤回公告（P2 修复：仅已发布可撤回）
     */
    public void revoke(Long id) {
        MsgAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        if (!"PUBLISHED".equals(announcement.getStatus())) {
            throw new BusinessException("仅已发布公告可撤回");
        }
        announcement.setStatus("REVOKED");
        announcementMapper.updateById(announcement);
    }
}
