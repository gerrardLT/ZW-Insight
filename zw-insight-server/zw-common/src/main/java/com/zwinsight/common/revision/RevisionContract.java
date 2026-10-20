package com.zwinsight.common.revision;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支出合同修订基准信息（V2026_123）：handler 校验合同后返回给 ContractRevisionService。
 */
@Data
@Builder
public class RevisionContract {

    private Long contractId;

    private Long projectId;

    /** 合同当前乐观锁版本 */
    private Integer version;

    /** 合同当前含税金额（修订前） */
    private BigDecimal contractAmount;

    /** 税率（%，可空；空表示历史合同税率未知） */
    private BigDecimal taxRate;

    /** 修订前合同 JSON 快照（由 handler 用各自实体序列化） */
    private String snapshot;
}
