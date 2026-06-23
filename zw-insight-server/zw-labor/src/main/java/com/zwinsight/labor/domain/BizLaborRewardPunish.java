package com.zwinsight.labor.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 劳务奖罚
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_labor_reward_punish")
public class BizLaborRewardPunish extends BaseEntity {

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
