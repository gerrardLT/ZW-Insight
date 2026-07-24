package com.zwinsight.purchase.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 报价截止时间
     */
    private LocalDateTime deadline;

    /**
     * 定标方式描述
     */
    private String awardMethod;

    /**
     * 询价描述/说明
     */
    private String description;

    /**
     * 技术要求
     */
    private String requirements;

    /**
     * 材料清单摘要
     */
    private String materialSummary;

    /**
     * 中标供应商名称
     */
    private String winnerName;

    /**
     * 中标金额
     */
    private BigDecimal winnerAmount;

    /**
     * 定标日期
     */
    private LocalDate awardDate;

    /**
     * 公示日期
     */
    private LocalDate publicizeDate;

    /**
     * 询价物料明细（非表字段，创建/更新时随主表一起提交并持久化到 biz_inquiry_item）
     */
    @TableField(exist = false)
    private List<BizInquiryItem> items;

    /**
     * 定向邀请供应商（非表字段，创建/更新时随主表一起提交并持久化到 biz_inquiry_supplier）
     */
    @TableField(exist = false)
    private List<BizInquirySupplier> suppliers;

    /**
     * 报价数量（非表字段，分页时按询价单聚合填充）
     */
    @TableField(exist = false)
    private Integer quotationCount;
}
