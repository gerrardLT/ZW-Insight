package com.zwinsight.finance.domain.dto;

import lombok.Data;

/**
 * 进项发票认领请求（V2026_123）：把发票库中未归属的发票关联到支出合同。
 * <p>项目归属由服务端按合同 project_id 推导并校验（禁止跨项目串账），不在请求体中传递。</p>
 */
@Data
public class InvoiceClaimRequest {

    /** 支出合同类别（PURCHASE/LABOR/MACHINE/SUBCONTRACT/OTHER_EXPENSE） */
    private String contractCategory;

    /** 关联支出合同ID（必填；回写合同累计收票） */
    private Long contractId;

    /** 供应商名称（可空，仅补全展示） */
    private String supplierName;
}
