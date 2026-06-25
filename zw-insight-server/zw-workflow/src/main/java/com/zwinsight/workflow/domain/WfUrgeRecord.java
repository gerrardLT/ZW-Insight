package com.zwinsight.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 催办记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_urge_record")
public class WfUrgeRecord extends BaseEntity {

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 被催办人用户ID
     */
    private String assignee;

    /**
     * 催办人（SYSTEM 表示系统自动 / 用户ID 表示手动催办）
     */
    private String urgeBy;

    /**
     * 催办类型：AUTO-自动催办 / MANUAL-手动催办
     */
    private String urgeType;

    /**
     * 催办消息内容
     */
    private String urgeMessage;

    /**
     * 催办时间
     */
    private LocalDateTime urgeTime;
}
