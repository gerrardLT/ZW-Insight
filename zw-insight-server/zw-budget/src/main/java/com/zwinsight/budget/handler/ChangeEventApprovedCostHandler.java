package com.zwinsight.budget.handler;

import com.zwinsight.budget.service.CostLedgerService;
import com.zwinsight.common.event.contract.ChangeEventApprovedEvent;
import com.zwinsight.common.event.outbox.OutboxEventHandler;
import com.zwinsight.common.event.outbox.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 变更事件批准 → CBS 当前预算传导处理器。
 *
 * <h3>它在主链中的位置</h3>
 * <pre>
 *   现场事件 → ChangeEvent → 影响评估 → 审批通过
 *                                          │
 *                                          ▼ （Outbox: CHANGE_EVENT_APPROVED）
 *                          ┌───────────────┴───────────────┐
 *                          ▼                               ▼
 *              本处理器：CBS current_amount 调整      合同累计变更金额回写
 *                          │                          （zw-contract 侧处理器）
 *                          ▼
 *                   成本流水台账留痕（可追溯 + 幂等）
 * </pre>
 *
 * <h3>为什么用事件而不是直接调用</h3>
 * <p>
 * zw-contract 若直接调 zw-budget 的 Service 改金额，两个模块就焊死了：
 * 预算模块改签名，合同模块跟着编译失败；更重要的是无法保证原子性——
 * 合同事务提交了但预算更新失败，就出现「变更批了、预算没变」的账实不符。
 * 走 Outbox 后，生产方只管投递，消费方按自己的节奏幂等消费，失败自动重试。
 * </p>
 *
 * <h3>幂等实现</h3>
 * <p>
 * 金额累加天然非幂等，因此以 {@code CHANGE_EVENT:{eventId}:{accountId}} 为幂等键
 * 落到成本流水表（DB 唯一索引兜底）。Outbox 重复投递时，
 * {@link CostLedgerService} 返回 DUPLICATE 而非抛异常，本处理器视为成功。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeEventApprovedCostHandler
        implements OutboxEventHandler<ChangeEventApprovedEvent> {

    /** 事件类型，须与生产方 ChangeEventEvents.TYPE_APPROVED 一致 */
    private static final String EVENT_TYPE = "CHANGE_EVENT_APPROVED";

    /** 幂等键前缀：与来源类型共同构成成本流水的 source_type + source_id */
    private static final String SOURCE_TYPE = CostLedgerService.SRC_CHANGE_EVENT;

    private final CostLedgerService costLedgerService;

    @Override
    public String supports() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage<ChangeEventApprovedEvent> message) {
        ChangeEventApprovedEvent event = message.getPayload();
        if (event == null) {
            // 负载缺失无法传导，重试也不会好转，但必须抛出以进入死信留证，
            // 绝不能静默返回成功——那会让「变更批了预算没动」永久无人知晓。
            throw new IllegalStateException("变更事件批准负载为空，outboxId=" + message.getId()
                    + ", rawPayload=" + message.getRawPayload());
        }
        if (event.getEventId() == null) {
            throw new IllegalStateException("变更事件批准负载缺少 eventId，outboxId=" + message.getId());
        }

        if (!event.hasAccountDetail()) {
            // 无明细：纯工期/范围变更，或评估时未指定账户。
            // 有成本影响却无明细属数据缺陷，需显式暴露而非静默跳过。
            if (event.hasCostImpact()) {
                log.warn("变更事件[{}]有成本影响 {} 但未指定受影响成本账户，无法传导到 CBS；"
                                + "请补充评估明细后重新批准（outboxId={}）",
                        event.getEventNumber(), event.getCostDelta(), message.getId());
                throw new IllegalStateException("变更事件[" + event.getEventNumber()
                        + "]缺少受影响成本账户明细，无法完成预算传导");
            }
            log.info("变更事件[{}]无成本影响，跳过 CBS 传导", event.getEventNumber());
            return;
        }

        // 交叉校验：明细合计应与评估总额一致，不一致说明评估数据自相矛盾
        BigDecimal detailSum = event.sumAccountDeltas();
        BigDecimal declared = event.getCostDelta() != null ? event.getCostDelta() : BigDecimal.ZERO;
        if (declared.signum() != 0 && detailSum.compareTo(declared) != 0) {
            log.warn("变更事件[{}]评估成本影响 {} 与账户明细合计 {} 不一致，以明细为准并留痕",
                    event.getEventNumber(), declared, detailSum);
        }

        List<CostLedgerService.PostCommand> commands = new ArrayList<>();
        for (ChangeEventApprovedEvent.AccountDelta delta : event.getAffectedAccounts()) {
            if (delta == null || delta.getAccountId() == null) {
                continue;
            }
            BigDecimal signed = delta.toSignedAmount();
            if (signed.signum() == 0) {
                continue;
            }
            // 幂等键带 accountId：同一变更事件影响多个账户时，每个账户各自独立去重，
            // 部分成功后重试不会把已记账的账户再加一遍。
            String sourceId = event.getEventId() + ":" + delta.getAccountId();
            commands.add(new CostLedgerService.PostCommand(
                    delta.getAccountId(),
                    CostLedgerService.AMT_CURRENT,
                    signed,
                    SOURCE_TYPE,
                    sourceId,
                    event.getEventNumber(),
                    event.getApprovedAt(),
                    "变更事件[" + event.getEventNumber() + "]批准传导"));
        }

        if (commands.isEmpty()) {
            log.info("变更事件[{}]账户明细均为零变动，无需记账", event.getEventNumber());
            return;
        }

        int posted = costLedgerService.postBatch(commands);
        log.info("变更事件[{}]已传导至 CBS：明细 {} 条，实际记账 {} 条（其余为幂等命中），成本影响={}",
                event.getEventNumber(), commands.size(), posted, declared);
    }
}
