package com.zwinsight.message.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内消息实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_message")
public class MsgMessage extends BaseEntity {

    /**
     * 接收用户ID
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 消息类型（APPROVAL-审批/WARNING-预警/SYSTEM-系统）
     */
    private String messageType;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 业务ID
     */
    private Long businessId;

    /**
     * 是否已读（0-未读 1-已读）
     */
    private Integer isRead;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;
}
