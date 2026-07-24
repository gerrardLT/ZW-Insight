package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 材料调拨单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_transfer")
public class BizMaterialTransfer extends BaseEntity {

    /** 调出项目ID */
    private Long fromProjectId;

    /** 调入项目ID */
    private Long toProjectId;

    /** 调拨日期 */
    private LocalDate transferDate;

    /** 状态（DRAFT-草稿/APPROVED-已审批） */
    private String status;

    /** 调拨明细（随主表提交，非表字段） */
    @TableField(exist = false)
    private List<BizMaterialTransferDetail> details;

    /** 调出项目名称（非本表字段，从 biz_project 回填） */
    @TableField(exist = false)
    private String fromProjectName;

    /** 调入项目名称（非本表字段，从 biz_project 回填） */
    @TableField(exist = false)
    private String toProjectName;
}
