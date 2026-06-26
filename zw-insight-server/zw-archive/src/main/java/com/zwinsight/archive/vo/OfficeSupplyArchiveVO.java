package com.zwinsight.archive.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 办公用品档案视图对象
 */
@Data
public class OfficeSupplyArchiveVO {

    /** 用品ID */
    private Long id;

    /** 用品名称 */
    private String supplyName;

    /** 当前库存 */
    private BigDecimal currentStock;

    /** 累计入库量 */
    private BigDecimal totalInbound;

    /** 累计领用量 */
    private BigDecimal totalIssued;

    /** 最近入库日期 */
    private LocalDate lastInboundDate;
}
