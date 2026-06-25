package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 质保金预警日志实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_retention_warning_log")
public class BizRetentionWarningLog extends BaseEntity {

    /** 质保金记录ID */
    private Long retentionId;

    /** 预警级别(UPCOMING/URGENT/OVERDUE) */
    private String warningLevel;

    /** 通知状态(PENDING/SENT/FAILED/PERMANENTLY_FAILED) */
    private String notifyStatus;

    /** 重试次数 */
    private Integer retryCount;

    /** 发送时间 */
    private LocalDateTime sentAt;
}
