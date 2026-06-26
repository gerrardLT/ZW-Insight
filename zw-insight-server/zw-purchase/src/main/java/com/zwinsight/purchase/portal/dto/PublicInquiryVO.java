package com.zwinsight.purchase.portal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公开询价列表 VO（免登录）
 */
@Data
public class PublicInquiryVO {

    /**
     * 询价ID
     */
    private Long id;

    /**
     * 询价标题
     */
    private String title;

    /**
     * 状态
     */
    private String status;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 报价截止时间
     */
    private LocalDateTime deadline;
}
