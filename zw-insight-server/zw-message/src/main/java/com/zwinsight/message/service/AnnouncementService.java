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
     * 更新公告
     */
    public void update(MsgAnnouncement announcement) {
        MsgAnnouncement existing = announcementMapper.selectById(announcement.getId());
        if (existing == null) {
            throw new BusinessException("公告不存在");
        }
        announcementMapper.updateById(announcement);
    }

    /**
     * 删除公告
     */
    public void delete(Long id) {
        announcementMapper.deleteById(id);
    }

    /**
     * 发布公告
     */
    public void publish(Long id) {
        MsgAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        announcement.setStatus("PUBLISHED");
        announcement.setPublishTime(LocalDateTime.now());
        announcementMapper.updateById(announcement);
    }

    /**
     * 撤回公告
     */
    public void revoke(Long id) {
        MsgAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        announcement.setStatus("REVOKED");
        announcementMapper.updateById(announcement);
    }
}
