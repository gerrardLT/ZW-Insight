package com.zwinsight.contract.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.contract.ChangeEventApprovedEvent;
import com.zwinsight.common.event.outbox.OutboxEventHandler;
import com.zwinsight.common.event.outbox.OutboxMessage;
import com.zwinsight.contract.domain.BizChangeEvent;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizChangeEventMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 变更事件批准 → 施工合同累计变更金额回写处理器。
 * <p>
 * 补齐 X4 横向联动断裂：当变更事件批准后，驱动施工合同的
 * {@code cumulative_change_amount} 累加，进而使产值上报额度上限
 * {@code contract_amount + cumulative_change_amount} 能够真实反映已批准的变更额。
 * </p>
 * <h3>幂等</h3>
 * <p>
 * {@code CHANGE_EVENT_APPROVED} 由多个 Outbox 处理器扇出消费（本处理器与
 * zw-budget 的 CBS 处理器），而 {@code OutboxDispatcher} 在任一处理器失败时
 * 会对全部处理器重试；金额累加天然非幂等，故以
 * {@code biz_change_event.contract_change_written} 为幂等标记，
 * 保证同一事件只回写一次（与 CBS 处理器以成本流水幂等键去重同源）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeEventApprovedContractHandler
        implements OutboxEventHandler<ChangeEventApprovedEvent> {

    private static final String EVENT_TYPE = "CHANGE_EVENT_APPROVED";

    private final BizConstructionContractMapper contractMapper;
    private final BizChangeEventMapper changeEventMapper;

    @Override
    public String supports() {
        return EVENT_TYPE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(OutboxMessage<ChangeEventApprovedEvent> message) {
        ChangeEventApprovedEvent event = message.getPayload();
        if (event == null || event.getEventId() == null) {
            log.warn("变更事件批准回写合同：事件负载为空，跳过处理，outboxId={}", message.getId());
            return;
        }

        BigDecimal costDelta = event.getCostDelta();
        if (costDelta == null || costDelta.signum() == 0) {
            log.info("变更事件[{}]无成本影响，跳过合同累计变更额回写", event.getEventNumber());
            return;
        }

        // 幂等守卫：同一事件只回写一次（多处理器扇出重试时防重复累加）
        BizChangeEvent entity = changeEventMapper.selectById(event.getEventId());
        if (entity == null) {
            log.warn("变更事件[{}]记录不存在，跳过合同累计变更额回写（eventId={}）",
                    event.getEventNumber(), event.getEventId());
            return;
        }
        if (Integer.valueOf(1).equals(entity.getContractChangeWritten())) {
            log.info("变更事件[{}]合同累计变更额已回写，跳过重复处理", event.getEventNumber());
            return;
        }

        Long targetContractId = resolveContractId(event);
        if (targetContractId == null) {
            return;
        }

        int rows = contractMapper.addCumulativeChangeAmount(targetContractId, costDelta);
        if (rows > 0) {
            entity.setContractChangeWritten(1);
            changeEventMapper.updateById(entity);
        }
        log.info("变更事件[{}]回写施工合同累计变更金额成功: contractId={}, delta={}, 影响行数={}",
                event.getEventNumber(), targetContractId, costDelta, rows);
    }

    /**
     * 解析回写目标合同：优先用事件显式指定的 contractId；
     * 未指定时仅当项目下恰有一份生效施工合同时自动匹配，多份则不猜测（跳过）。
     */
    private Long resolveContractId(ChangeEventApprovedEvent event) {
        if (event.getContractId() != null) {
            return event.getContractId();
        }
        if (event.getProjectId() == null) {
            return null;
        }
        LambdaQueryWrapper<BizConstructionContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizConstructionContract::getProjectId, event.getProjectId())
                .eq(BizConstructionContract::getStatus, "EFFECTIVE");
        List<BizConstructionContract> activeContracts = contractMapper.selectList(wrapper);
        if (activeContracts.size() == 1) {
            Long id = activeContracts.get(0).getId();
            log.info("变更事件[{}]未指定合同ID，自动匹配项目下唯一生效施工合同: contractId={}",
                    event.getEventNumber(), id);
            return id;
        }
        if (activeContracts.size() > 1) {
            log.warn("变更事件[{}]所属项目下存在多份生效施工合同（{} 份），未指定 contractId 时不自动猜测，跳过合同回写",
                    event.getEventNumber(), activeContracts.size());
        } else {
            log.info("变更事件[{}]所属项目暂无生效施工合同，跳过合同回写", event.getEventNumber());
        }
        return null;
    }
}
