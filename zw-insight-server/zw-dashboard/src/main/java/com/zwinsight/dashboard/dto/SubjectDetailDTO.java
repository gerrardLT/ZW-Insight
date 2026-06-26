package com.zwinsight.dashboard.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算科目执行明细
 */
@Data
public class SubjectDetailDTO {

    /** 科目名称 */
    private String subjectName;

    /** 预算金额 */
    private BigDecimal budget;

    /** 已付金额 */
    private BigDecimal paid;

    /** 占比（保留4位小数） */
    private BigDecimal ratio;
}
