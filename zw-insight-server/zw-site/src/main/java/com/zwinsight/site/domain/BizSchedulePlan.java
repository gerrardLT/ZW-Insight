package com.zwinsight.site.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 进度计划实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_schedule_plan")
public class BizSchedulePlan extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 任务名称 */
    private String taskName;

    /** 父任务ID（0表示顶级） */
    private Long parentId;

    /** 计划开始日期 */
    private LocalDate planStartDate;

    /** 计划结束日期 */
    private LocalDate planEndDate;

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际结束日期 */
    private LocalDate actualEndDate;

    /** 进度（百分比） */
    private BigDecimal progress;

    /** 任务状态（NOT_STARTED/IN_PROGRESS/COMPLETED/DELAYED） */
    private String taskStatus;

    /** 任务详情 */
    private String taskDetail;

    /** 负责人 */
    private String responsible;

    /** 排序号 */
    private Integer sortOrder;

    /** 子任务列表（非数据库字段） */
    @TableField(exist = false)
    private List<BizSchedulePlan> children;

    /** 所属项目名称（透传，非本表字段） */
    @TableField(exist = false)
    private String projectName;
}
