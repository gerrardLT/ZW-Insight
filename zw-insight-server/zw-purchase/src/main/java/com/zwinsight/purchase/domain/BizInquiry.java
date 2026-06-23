package com.zwinsight.purchase.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 询价单实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inquiry")
public class BizInquiry extends BaseEntity {

    /**
     * 询价标题
     */
    private String title;

    /**
     * 邀请模式（PUBLIC-公开/DIRECTED-定向）
     */
    private String inviteMode;

    /**
     * 定标模式（LOWEST-最低价/COMPREHENSIVE-综合评审）
     */
    private String bidMode;

    /**
     * 状态（DRAFT-草稿/PUBLISHED-已发布/QUOTED-已报价/AWARDED-已定标/ANNOUNCED-已公示）
     */
    private String status;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;
}
