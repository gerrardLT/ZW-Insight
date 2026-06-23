package com.zwinsight.subcontract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 分包奖罚
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_subcontract_reward_punish")
public class BizSubcontractRewardPunish extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 合同ID */
    private Long contractId;

    /** 奖罚类型（REWARD-奖励/PUNISH-处罚） */
    private String rpType;

    /** 金额 */
    private BigDecimal amount;

    /** 原因 */
    private String reason;
}
