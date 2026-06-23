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

    /** 中标信息 */
    private String winInfo;

    /** 状态 */
    private String status;
}
