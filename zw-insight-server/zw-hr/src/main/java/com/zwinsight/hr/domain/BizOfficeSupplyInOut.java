package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 办公用品出入库实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_office_supply_in_out")
public class BizOfficeSupplyInOut extends BaseEntity {

    /** 用品ID */
    private Long supplyId;

    /** 出入库类型（IN/OUT） */
    private String ioType;

    /** 数量 */
    private Integer quantity;

    /** 备注 */
    private String remark;

    /** 状态（DRAFT/APPROVED） */
    private String status;
}
