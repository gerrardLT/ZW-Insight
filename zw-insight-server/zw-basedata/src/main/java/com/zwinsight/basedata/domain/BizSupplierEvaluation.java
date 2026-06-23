package com.zwinsight.basedata.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 供应商评价实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_supplier_evaluation")
public class BizSupplierEvaluation extends BaseEntity {

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 评价日期 */
    private LocalDate evaluationDate;

    /** 质量评分（1-5） */
    private Integer qualityScore;

    /** 及时性评分（1-5） */
    private Integer timelinessScore;

    /** 价格评分（1-5） */
    private Integer priceScore;

    /** 服务评分（1-5） */
    private Integer serviceScore;

    /** 合作评分（1-5） */
    private Integer cooperationScore;

    /** 综合评分 */
    private BigDecimal totalScore;

    /** 备注 */
    private String remark;
}
