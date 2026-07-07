package com.zwinsight.material.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存安全阈值配置
 * <p>
 * 为每个项目+材料组合配置安全库存数量，当实际库存低于此阈值时触发预警通知。
 * projectId=NULL 表示全局默认配置。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_stock_warning_config")
public class BizStockWarningConfig extends BaseEntity {

    /** 项目ID（NULL=全局默认） */
    private Long projectId;

    /** 材料ID */
    private Long materialId;

    /** 材料名称（冗余，便于展示） */
    private String materialName;

    /** 安全库存数量（低于此值触发预警） */
    private BigDecimal safetyStock;

    /** 是否启用预警（1-启用 0-停用） */
    private Integer enabled;
}
