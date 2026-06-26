package com.zwinsight.contract.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 合同到期信息 DTO
 * <p>
 * 用于合同到期扫描任务中传递合同的基本信息，
 * 支持多种合同类型（采购/分包/机械/劳务）的统一处理。
 * </p>
 */
@Data
public class ContractExpiryDTO {

    /**
     * 合同ID
     */
    private Long id;

    /**
     * 合同编号
     */
    private String contractCode;

    /**
     * 合同名称
     */
    private String contractName;

    /**
     * 合同到期日期
     */
    private LocalDate endDate;

    /**
     * 合同分类(MATERIAL/LABOR/MACHINE/SUBCONTRACT)
     */
    private String contractCategory;

    /**
     * 对方名称（供应商/分包商名称）
     */
    private String counterpartName;

    /**
     * 合同状态
     */
    private String status;

    /**
     * 合同负责人ID
     */
    private Long responsibleUserId;

    /**
     * 合同来源表标识
     */
    private String contractTable;
}
