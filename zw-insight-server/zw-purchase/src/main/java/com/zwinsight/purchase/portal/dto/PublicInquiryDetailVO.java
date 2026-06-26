package com.zwinsight.purchase.portal.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开询价详情 VO（免登录）
 */
@Data
public class PublicInquiryDetailVO {

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

    /**
     * 物料明细列表
     */
    private List<InquiryItemVO> items;

    @Data
    public static class InquiryItemVO {
        /**
         * 材料名称
         */
        private String materialName;

        /**
         * 规格
         */
        private String specification;

        /**
         * 单位
         */
        private String unit;

        /**
         * 数量
         */
        private java.math.BigDecimal quantity;
    }
}
