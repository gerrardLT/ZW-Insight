package com.zwinsight.finance.listener;

import com.zwinsight.common.event.finance.ExpenseSettlementPayableEvent;
import com.zwinsight.finance.service.PayableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 支出结算 → 应付台账监听器（V2026_115）。
 * <p>生产方：zw-subcontract / zw-purchase / zw-labor / zw-machine 的结算生效与删除冲销路径，
 * 经 zw-common 契约事件 {@link ExpenseSettlementPayableEvent} 解耦（模块依赖方向不可反转）。</p>
 * <p>同步 {@code @EventListener}：在发布方事务内执行，消费失败整体回滚，
 * 不允许「结算已批但应付未生成」的半成品；幂等由 PayableService 按来源键保证。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseSettlementPayableListener {

    private final PayableService payableService;

    @EventListener
    public void onEvent(ExpenseSettlementPayableEvent event) {
        if (event.isReversed()) {
            payableService.reduceOnSettlementReverse(event);
        } else {
            payableService.generateFromSettlement(event);
        }
    }
}
