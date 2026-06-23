package com.zwinsight.message.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_template")
public class MsgTemplate extends BaseEntity {

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 模板内容
     */
    private String content;

    /**
     * 渠道类型（逗号分隔，如：SMS,EMAIL,WS）
     */
    private String channelTypes;

    /**
     * 状态（1-启用 0-停用）
     */
    private Integer status;
}
