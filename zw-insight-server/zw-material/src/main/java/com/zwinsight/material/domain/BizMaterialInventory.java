package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 材料盘点单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_inventory")
public class BizMaterialInventory extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 盘点日期 */
    private LocalDate inventoryDate;

    /** 状态（DRAFT-草稿/APPROVED-已审批） */
    private String status;
}
