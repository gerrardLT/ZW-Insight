package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 合同明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contract_detail")
public class BizContractDetail extends BaseEntity {

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 合同表名（区分合同来源）
     */
    private String contractTable;

    /**
     * 项目名称
     */
    private String itemName;

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
     * 单价
     */
    private BigDecimal unitPrice;

    /**
     * 合计金额
     */
    private BigDecimal totalPrice;

    /**
     * 备注
     */
    private String remark;

    /**
     * 排序号
     */
    private Integer sortOrder;
}
