package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 合同到期提醒日志实体
 */
@Data
@TableName("biz_contract_expiry_log")
public class BizContractExpiryLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 合同表名
     */
    private String contractTable;

    /**
     * 合同编号
     */
    private String contractCode;

    /**
     * 合同类型
     */
    private String contractCategory;

    /**
     * 提醒级别（UPCOMING/URGENT）
     */
    private String level;

    /**
     * 剩余天数
     */
    private Integer remainingDays;

    /**
     * 通知人ID
     */
    private Long notifyUserId;

    /**
     * 通知状态（SENT/FAILED）
     */
    private String notifyStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
