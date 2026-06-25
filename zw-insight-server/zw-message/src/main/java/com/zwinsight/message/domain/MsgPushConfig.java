package com.zwinsight.message.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息推送渠道配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("msg_push_config")
public class MsgPushConfig extends BaseEntity {

    /**
     * 业务类型编码（对应 workflow business-type 编码）
     */
    private String businessType;

    /**
     * 业务类型名称
     */
    private String businessTypeName;

    /**
     * 是否启用站内信
     */
    private Boolean enableInApp;

    /**
     * 是否启用短信
     */
    private Boolean enableSms;

    /**
     * 是否启用邮件
     */
    private Boolean enableEmail;

    /**
     * 是否启用APP推送
     */
    private Boolean enableAppPush;

    /**
     * 站内信模板ID
     */
    private Long inAppTemplateId;

    /**
     * 短信模板ID
     */
    private Long smsTemplateId;

    /**
     * 邮件模板ID
     */
    private Long emailTemplateId;
}
