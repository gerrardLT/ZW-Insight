package com.zwinsight.finance.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 财务封账记录 DTO
 */
@Data
public class FinanceLockDTO {

    /** 主键ID */
    private Long id;

    /** 封账期间，格式 YYYY-MM */
    private String period;

    /** 封账类型：MONTHLY / QUARTERLY */
    private String lockType;

    /** 状态：LOCKED / UNLOCKED */
    private String status;

    /** 封账操作人ID */
    private Long lockBy;

    /** 封账时间 */
    private LocalDateTime lockTime;

    /** 解封操作人ID */
    private Long unlockBy;

    /** 解封时间 */
    private LocalDateTime unlockTime;
}
