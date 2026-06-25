package com.zwinsight.site.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 整改催办日志实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_reminder_log")
public class BizReminderLog extends BaseEntity {

    /**
     * 检查记录ID(biz_inspection.id)
     */
    private Long inspectionId;

    /**
     * 接收人ID
     */
    private Long receiverId;

    /**
     * 催办级别(NORMAL/ESCALATED)
     */
    private String reminderLevel;

    /**
     * 发送状态(SENT/FAILED)
     */
    private String sendStatus;

    /**
     * 超期天数
     */
    private Integer overdueDays;

    /**
     * 发送时间
     */
    private LocalDateTime sentAt;
}
