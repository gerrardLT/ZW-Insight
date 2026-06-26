package com.zwinsight.subcontract.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 分包结算单创建请求
 */
@Data
public class SubcontractSettlementCreateRequest {

    /** 合同ID */
    @NotNull(message = "合同ID不能为空")
    private Long contractId;

    /** 项目ID */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 结算明细行列表 */
    @NotEmpty(message = "结算明细不能为空")
    @Valid
    private List<SubcontractSettlementDetailDTO> details;
}
