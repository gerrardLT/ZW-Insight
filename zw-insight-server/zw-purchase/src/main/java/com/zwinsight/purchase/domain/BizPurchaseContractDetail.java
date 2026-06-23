package com.zwinsight.purchase.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 采购合同明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_purchase_contract_detail")
public class BizPurchaseContractDetail extends BaseEntity {

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 材料名称
     */
    private String materialName;

    /**
     * 规格
     */
    private String specification;

    /**
     * 单位
     */
    private String unit;

    /**
     * 数量
     */
    private BigDecimal quantity;

    /**
     * 合同数量
     */
    private BigDecimal contractQuantity;

    /**
     * 合同单价
     */
    private BigDecimal contractPrice;

    /**
     * 排序号
     */
    private Integer sortOrder;
}
