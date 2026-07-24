package com.zwinsight.message.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.message.domain.MsgNotice;
import com.zwinsight.message.mapper.MsgNoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 通知服务
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final MsgNoticeMapper noticeMapper;

    /**
     * 分页查询通知
     */
    public PageResult<MsgNotice> page(int page, int size, String title, String status) {
        Page<MsgNotice> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<MsgNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(title), MsgNotice::getTitle, title)
                .eq(StrUtil.isNotBlank(status), MsgNotice::getStatus, status)
                .orderByDesc(MsgNotice::getCreatedAt);
        Page<MsgNotice> result = noticeMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public MsgNotice getById(Long id) {
        MsgNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("通知不存在");
        }
        return notice;
    }

    /**
     * 新增通知
     */
    public void save(MsgNotice notice) {
        notice.setStatus("DRAFT");
        noticeMapper.insert(notice);
    }

    /**
     * 发布通知
     */
    public void publish(Long id) {
        MsgNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("通知不存在");
        }
        notice.setStatus("PUBLISHED");
        noticeMapper.updateById(notice);
    }
}
