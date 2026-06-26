package com.zwinsight.subcontract.dto;

import com.zwinsight.subcontract.domain.BizSubcontractSettlement;
import com.zwinsight.subcontract.domain.BizSubcontractSettlementDetail;
import lombok.Data;

import java.util.List;

/**
 * 分包结算单详情 VO（含明细行列表）
 */
@Data
public class SubcontractSettlementDetailVO {

    /** 结算单主表信息 */
    private BizSubcontractSettlement settlement;

    /** 合同编号 */
    private String contractCode;

    /** 合同名称 */
    private String contractName;

    /** 分包方 */
    private String subcontractor;

    /** 明细行列表 */
    private List<BizSubcontractSettlementDetail> details;
}
