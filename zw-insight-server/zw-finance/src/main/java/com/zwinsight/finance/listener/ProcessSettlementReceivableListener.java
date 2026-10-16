package com.zwinsight.finance.listener;

import com.zwinsight.common.event.contract.ProcessSettlementApprovedEvent;
import com.zwinsight.finance.service.ReceivableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 过程结算生效 → 应收台账生成监听器（V2026_108）。
 * <p>
 * zw-contract 生产 {@code ProcessSettlementApprovedEvent}，本监听器在 zw-finance 消费：
 * zw-finance 已依赖 zw-contract，直接注入 {@code FinalSettlementService} 会形成
 * Maven 循环依赖，故走 Spring 事件（与 {@code ChangeEventApprovedEvent} 家族同模式）。
 * </p>
 * <p>
 * <b>不吞异常</b>：同步 {@code @EventListener} 在发布方事务内执行，应收生成失败
 * 使过程结算审批整体回滚，不允许「结算已批但应收未生成」的半成品。
 * 幂等由 {@code ReceivableService#generateFromProcessSettlement} 按 settlementId 保证。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessSettlementReceivableListener {

    private final ReceivableService receivableService;

    @EventListener
    public void onProcessSettlementApproved(ProcessSettlementApprovedEvent event) {
        log.info("过程结算生效事件：开始生成应收, settlementId={}, netAmount={}",
                event.getSettlementId(), event.getNetAmount());
        receivableService.generateFromProcessSettlement(event);
    }
}
