package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 办公用品实体
 * <p>注意：与 {@code com.zwinsight.archive.domain.BizOfficeSupply} 映射同一张表 {@code biz_office_supply}。
 * 档案页展示的 current_stock/total_inbound/total_issued/last_inbound_date 四列此前只声明在 archive 侧，
 * HR 侧不声明 → 出入库提交时无法回写（2026-10-12 补声明）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_office_supply")
public class BizOfficeSupply extends BaseEntity {

    /** 分类名称 */
    private String categoryName;

    /** 用品名称 */
    private String supplyName;

    /** 规格 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 图片URL */
    private String imageUrl;

    /** 库存数量 */
    private Integer stockQuantity;

    /** 当前库存（档案页口径，与 stockQuantity 保持一致） */
    private BigDecimal currentStock;

    /** 累计入库数量 */
    private BigDecimal totalInbound;

    /** 累计出库数量 */
    private BigDecimal totalIssued;

    /** 最近入库日期 */
    private LocalDate lastInboundDate;

    /** 状态（1-启用 0-停用） */
    private Integer status;
}
