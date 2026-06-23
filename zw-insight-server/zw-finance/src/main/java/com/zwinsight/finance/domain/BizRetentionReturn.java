package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 质保金返还实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_retention_return")
public class BizRetentionReturn extends BaseEntity {

    /** 质保金ID */
    private Long retentionId;

    /** 返还金额 */
    private BigDecimal returnAmount;

    /** 返还日期 */
    private LocalDate returnDate;

    /** 状态（DRAFT-草稿/APPROVED-已审批） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
