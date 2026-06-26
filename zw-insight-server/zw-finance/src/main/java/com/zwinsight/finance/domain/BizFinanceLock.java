package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 财务封账记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_finance_lock")
public class BizFinanceLock extends BaseEntity {

    /** 封账期间，格式 YYYY-MM */
    private String period;

    /** 封账类型：MONTHLY-月度 / QUARTERLY-季度 */
    private String lockType;

    /** 状态：LOCKED-已封账 / UNLOCKED-已解封 */
    private String status;

    /** 项目ID（NULL表示全局封账） */
    private Long projectId;

    /** 封账操作人ID */
    private Long lockBy;

    /** 封账时间 */
    private LocalDateTime lockTime;

    /** 解封操作人ID */
    private Long unlockBy;

    /** 解封时间 */
    private LocalDateTime unlockTime;

    /** 租户编码 */
    private String tenantCode;
}
