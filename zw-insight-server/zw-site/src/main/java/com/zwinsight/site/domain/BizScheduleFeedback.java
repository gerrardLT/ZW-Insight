package com.zwinsight.site.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 进度反馈实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_schedule_feedback")
public class BizScheduleFeedback extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 计划任务ID */
    private Long planId;

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际结束日期 */
    private LocalDate actualEndDate;

    /** 任务状态 */
    private String taskStatus;

    /** 完成进度 */
    private BigDecimal progress;

    /** 备注 */
    private String remark;

    /** 状态（DRAFT/APPROVED） */
    private String status;
}
