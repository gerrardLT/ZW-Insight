package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 跨项目资金调度
 * <p>
 * 业务场景：
 * - 从公司资金池拨付到项目账户（from_project_id=NULL）
 * - 从项目A调拨到项目B
 * - 从项目账户回收到公司资金池（to_project_id=NULL）
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_fund_transfer")
public class BizFundTransfer extends BaseEntity {

    /** 调拨编号（自动生成） */
    private String transferCode;

    /** 调出项目ID（NULL=公司资金池） */
    private Long fromProjectId;

    /** 调入项目ID（NULL=公司资金池） */
    private Long toProjectId;

    /** 调拨金额 */
    private BigDecimal transferAmount;

    /** 调拨日期 */
    private LocalDate transferDate;

    /** 调拨原因 */
    private String transferReason;

    /** 调出账户ID */
    private Long fromAccountId;

    /** 调入账户ID */
    private Long toAccountId;

    /** 状态（DRAFT/APPROVED/REJECTED） */
    private String status;

    /** 审批流程实例ID */
    private String workflowInstanceId;
}
