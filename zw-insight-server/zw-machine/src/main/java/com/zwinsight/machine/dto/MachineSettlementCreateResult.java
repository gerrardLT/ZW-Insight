package com.zwinsight.machine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建结算单返回结果
 * <p>包含结算单ID和被排除的已结算工作日志信息</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MachineSettlementCreateResult {

    /** 结算单ID */
    private Long settlementId;

    /** 被排除的已结算工作日志数量 */
    private int excludedWorkLogCount;

    /** 被排除的已结算工作日志ID列表 */
    private List<Long> excludedWorkLogIds;

    public MachineSettlementCreateResult(Long settlementId) {
        this.settlementId = settlementId;
        this.excludedWorkLogCount = 0;
        this.excludedWorkLogIds = List.of();
    }
}
