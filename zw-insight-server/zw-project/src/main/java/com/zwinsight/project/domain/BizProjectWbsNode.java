package com.zwinsight.project.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * WBS 节点实体（Work Breakdown Structure，工作分解结构）
 * <p>
 * 项目范围的主数据结构：项目 → 阶段(level=1) → 工作包(level=2) → 任务(level=3)。
 * CBS 成本账户通过 {@code wbs_node_id} 挂到工作包上，使「成本」与「范围/进度」
 * 共享同一套分解口径，避免各模块自建维度导致的口径分裂。
 * </p>
 * <p>列名使用 node_ 前缀（node_code/node_name/node_level），规避 SQL 保留字。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_project_wbs_node")
public class BizProjectWbsNode extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 父节点ID（NULL=根节点） */
    private Long parentId;

    /** 层级（1-阶段 2-工作包 3-任务） */
    private Integer nodeLevel;

    /** 节点编号（如 PH-001/WP-001），租户+项目内唯一 */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 描述 */
    private String description;

    /** 计划开始日期 */
    private LocalDate startDate;

    /** 计划结束日期 */
    private LocalDate endDate;

    /** 状态（ACTIVE-进行中/INACTIVE-停用/CLOSED-已关闭） */
    private String status;

    /** 排序号 */
    private Integer sortOrder;

    /** 项目名称（冗余展示字段，不持久化） */
    @TableField(exist = false)
    private String projectName;

    /** 子节点（树形查询时内存装配，不持久化） */
    @TableField(exist = false)
    private List<BizProjectWbsNode> children = new ArrayList<>();
}
