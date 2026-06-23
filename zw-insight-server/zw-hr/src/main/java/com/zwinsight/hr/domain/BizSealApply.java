package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用印申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_seal_apply")
public class BizSealApply extends BaseEntity {

    /** 申请人 */
    private String applicant;

    /** 印章类型 */
    private String sealType;

    /** 是否外带（0-否 1-是） */
    private Integer isCarryOut;

    /** 使用时间 */
    private LocalDateTime useTime;

    /** 事由 */
    private String reason;

    /** 状态（DRAFT/APPROVED） */
    private String status;
}
