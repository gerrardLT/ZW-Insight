package com.zwinsight.message.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.message.domain.MsgNotice;
import com.zwinsight.message.mapper.MsgNoticeMapper;
import com.zwinsight.security.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 通知服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final MsgNoticeMapper noticeMapper;
    private final MessageService messageService;
    private final SysUserMapper sysUserMapper;

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
     * 发布通知：置 PUBLISHED 并为接收对象生成站内消息。
     * <p>接收对象取 {@code targetUserIds}（逗号分隔）；未指定时按「全部人员」广播到
     * 本租户全部启用用户。已发布的通知拒绝重复发布，避免重复推送站内消息。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        MsgNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("通知不存在");
        }
        if ("PUBLISHED".equals(notice.getStatus())) {
            throw new BusinessException("通知已发布，请勿重复发布");
        }
        notice.setStatus("PUBLISHED");
        noticeMapper.updateById(notice);

        List<Long> targets = resolveTargets(notice.getTargetUserIds());
        if (targets.isEmpty()) {
            log.warn("通知发布未匹配到接收人，仅更新状态不生成站内消息, noticeId={}", id);
            return;
        }
        for (Long userId : targets) {
            messageService.sendMessage(userId, notice.getTitle(), notice.getContent(),
                    "SYSTEM", "NOTICE", notice.getId());
        }
        log.info("通知已发布并生成站内消息, noticeId={}, 接收人数={}", id, targets.size());
    }

    /** 解析接收对象：指定用户优先，未指定则广播本租户全部启用用户 */
    private List<Long> resolveTargets(String targetUserIds) {
        Set<Long> targets = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(targetUserIds)) {
            for (String part : targetUserIds.split(",")) {
                String text = part.trim();
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    targets.add(Long.valueOf(text));
                } catch (NumberFormatException e) {
                    log.warn("通知接收人ID非法，已忽略: {}", text);
                }
            }
            return new ArrayList<>(targets);
        }
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            log.warn("通知广播跳过：租户上下文缺失");
            return new ArrayList<>();
        }
        List<Long> all = sysUserMapper.selectActiveUserIds(tenantId);
        return all == null ? new ArrayList<>() : all;
    }
}
