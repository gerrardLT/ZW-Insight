package com.zwinsight.tender.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 开标记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_open_bid_record")
public class BizOpenBidRecord extends BaseEntity {

    /** 投标登记ID */
    private Long registerId;

    /** 项目ID */
    private Long projectId;

    /** 是否中标（0-未中标 1-中标） */
    private Integer isWon;

    /** 开标金额（中标价或最低报价，V2026_82） */
    private java.math.BigDecimal bidAmount;

    /** 中标信息 */
    private String winInfo;

    /** 落标原因分类（PRICE_OVER/TECH_WEAK/BIZ_DEVIATION/CREDIT_LACK/OTHER，V2026_82 B4） */
    private String lostReasonCategory;

    /** 状态 */
    private String status;
}
