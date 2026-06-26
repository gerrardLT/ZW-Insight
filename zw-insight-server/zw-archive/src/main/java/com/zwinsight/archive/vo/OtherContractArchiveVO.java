package com.zwinsight.archive.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 其它合同档案视图对象（其它收入合同/其它支出合同）
 */
@Data
public class OtherContractArchiveVO {

    /** 合同ID */
    private Long id;

    /** 合同编号 */
    private String contractCode;

    /** 合同名称 */
    private String contractName;

    /** 金额 */
    private BigDecimal contractAmount;

    /** 签约日期 */
    private LocalDate signingDate;

    /** 状态 */
    private String status;

    /** 关联项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;
}
