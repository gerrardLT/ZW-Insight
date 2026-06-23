package com.zwinsight.message.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_notice")
public class MsgNotice extends BaseEntity {

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 目标用户ID集合（逗号分隔）
     */
    private String targetUserIds;

    /**
     * 通知类型（SYSTEM-系统通知/BUSINESS-业务通知）
     */
    private String noticeType;

    /**
     * 状态（DRAFT-草稿/PUBLISHED-已发布）
     */
    private String status;
}
