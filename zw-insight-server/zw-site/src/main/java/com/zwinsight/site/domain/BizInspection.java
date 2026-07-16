package com.zwinsight.site.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 质量安全检查实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inspection")
public class BizInspection extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 检查类型（QUALITY/SAFETY） */
    private String inspectionType;

    /** 检查方案ID */
    private Long schemeId;

    /** 检查方案快照（JSON格式） */
    private String schemeSnapshot;

    /** 检查内容 */
    private String inspectionContent;

    /** 是否存在问题（0-无 1-有） */
    private Integer hasProblem;

    /** 问题描述 */
    private String problemDescription;

    /** 整改责任人ID */
    private Long responsiblePersonId;

    /** 整改期限 */
    private LocalDate rectificationDeadline;

    /** 整改完成日期 */
    private LocalDate rectificationDate;

    /** 整改状态（PENDING/SUBMITTED/APPROVED/REJECTED） */
    private String rectificationStatus;

    /** 检查明细（随主表提交，非表字段） */
    @TableField(exist = false)
    private List<BizInspectionDetail> details;
}
