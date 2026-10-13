package com.zwinsight.common.event.labor;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 劳务工资单审批通过领域事件。
 * <p>
 * 当劳务工资单在流程引擎审批通过后发布，用于触发财务农民工工资专户
 * 自动生成待代发台账记录（PENDING），打通劳务用工与总包专户代发的合规链条。
 * </p>
 */
@Getter
public class LaborPayrollApprovedEvent extends ApplicationEvent {

    private final Long payrollId;
    private final Long projectId;
    private final Long teamId;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final BigDecimal totalAmount;

    public LaborPayrollApprovedEvent(Object source, Long payrollId, Long projectId,
                                     Long teamId, LocalDate periodStart,
                                     LocalDate periodEnd, BigDecimal totalAmount) {
        super(source);
        this.payrollId = payrollId;
        this.projectId = projectId;
        this.teamId = teamId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalAmount = totalAmount;
    }
}
