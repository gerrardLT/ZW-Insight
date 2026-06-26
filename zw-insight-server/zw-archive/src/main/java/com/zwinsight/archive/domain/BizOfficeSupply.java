package com.zwinsight.archive.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 办公用品库存
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_office_supply")
public class BizOfficeSupply extends BaseEntity {

    /** 用品名称 */
    private String supplyName;

    /** 规格型号 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 当前库存数量 */
    private BigDecimal currentStock;

    /** 累计入库量 */
    private BigDecimal totalInbound;

    /** 累计领用量 */
    private BigDecimal totalIssued;

    /** 最近入库日期 */
    private LocalDate lastInboundDate;
}
