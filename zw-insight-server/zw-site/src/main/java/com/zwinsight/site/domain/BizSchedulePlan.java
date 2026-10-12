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

    /**
     * 关联 WBS 节点ID（可空）。
     * 2026-10-12（V2026_96）新增：把进度任务可选地挂到 WBS 节点，
     * 打通「成本侧结构（CBS/变更都挂 WBS）」与「工程侧结构（进度计划）」，为成本-进度对账铺路。
     */
    private Long wbsNodeId;

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

    /**
     * 是否滞后（派生字段，非数据库字段）。
     * 判定：计划结束日期早于今天 且 任务状态非 COMPLETED。
     * 2026-10-12 新增——此前 DELAYED 状态在全后端无任何写入点，滞后无从判定，
     * 现改为查询时动态推导，避免落库与人工维护的状态互相覆盖。
     */
    @TableField(exist = false)
    private Boolean delayed;

    /** 滞后天数（派生字段，非数据库字段）：今天 − 计划结束日期，未滞后为 0 */
    @TableField(exist = false)
    private Integer delayDays;
}
