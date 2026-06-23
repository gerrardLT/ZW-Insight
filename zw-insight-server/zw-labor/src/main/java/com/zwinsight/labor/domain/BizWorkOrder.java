package com.zwinsight.labor.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 派工单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_work_order")
public class BizWorkOrder extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 班组ID */
    private Long teamId;

    /** 工人ID */
    private Long workerId;

    /** 工人姓名 */
    private String workerName;

    /** 工作日期 */
    private LocalDate workDate;

    /** 工时 */
    private BigDecimal hours;

    /** 时薪 */
    private BigDecimal hourlyRate;

    /** 加班工时 */
    private BigDecimal overtime;

    /** 加班费率 */
    private BigDecimal overtimeRate;

    /** 合计金额 */
    private BigDecimal totalAmount;

    /** 用工类型（FIXED-固定/TEMPORARY-临时） */
    private String orderType;

    /** 状态（DRAFT-草稿/APPROVED-已审批） */
    private String status;
}
