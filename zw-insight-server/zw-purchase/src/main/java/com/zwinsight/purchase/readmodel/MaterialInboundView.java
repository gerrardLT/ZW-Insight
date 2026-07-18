package com.zwinsight.purchase.readmodel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 材料入库单只读视图（供采购结算跨模块读取入库依据用）。
 * <p>
 * zw-purchase 不依赖 zw-material（zw-material 反向依赖 zw-purchase，
 * 反向引用会造成循环依赖），故采购结算需要入库量/入库金额时，
 * 通过只读 mapper 直接查询 biz_material_inbound 表，不引用 material 模块的实体/服务。
 * </p>
 */
@Data
public class MaterialInboundView {

    /**
     * 入库单ID
     */
    private Long id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 采购合同ID
     */
    private Long contractId;

    /**
     * 入库单号
     */
    private String inboundCode;

    /**
     * 入库日期
     */
    private LocalDate inboundDate;

    /**
     * 入库总金额
     */
    private BigDecimal totalAmount;

    /**
     * 状态（DRAFT-草稿/APPROVED-已审批）
     */
    private String status;
}
