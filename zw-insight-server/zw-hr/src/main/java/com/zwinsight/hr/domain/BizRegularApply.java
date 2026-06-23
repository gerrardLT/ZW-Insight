package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 转正申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_regular_apply")
public class BizRegularApply extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 用户姓名 */
    private String userName;

    /** 试用期结束日期 */
    private LocalDate trialEndDate;

    /** 转正日期 */
    private LocalDate regularDate;

    /** 状态（DRAFT/APPROVED） */
    private String status;
}
