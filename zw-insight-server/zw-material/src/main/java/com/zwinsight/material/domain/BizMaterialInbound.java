package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 材料入库单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_inbound")
public class BizMaterialInbound extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 采购合同ID */
    private Long contractId;

    /** 入库单号 */
    private String inboundCode;

    /** 入库日期 */
    private LocalDate inboundDate;

    /** 入库总金额 */
    private BigDecimal totalAmount;

    /** 直接出库（0-否 1-是） */
    private Integer directOutbound;

    /** 状态（DRAFT-草稿/APPROVED-已审批） */
    private String status;
}
