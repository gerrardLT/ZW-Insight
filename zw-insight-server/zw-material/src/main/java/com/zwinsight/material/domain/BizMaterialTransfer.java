package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

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
}
