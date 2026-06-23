package com.zwinsight.message.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公告实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_announcement")
public class MsgAnnouncement extends BaseEntity {

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 发布范围（ALL-全部/DEPT-部门/USER-指定用户）
     */
    private String publishScope;

    /**
     * 范围ID集合（逗号分隔）
     */
    private String scopeIds;

    /**
     * 是否置顶（0-否 1-是）
     */
    private Integer isTop;

    /**
     * 生效开始日期
     */
    private LocalDate effectiveStart;

    /**
     * 生效结束日期
     */
    private LocalDate effectiveEnd;

    /**
     * 状态（DRAFT-草稿/PUBLISHED-已发布/REVOKED-已撤回）
     */
    private String status;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;
}
