package com.zwinsight.contract.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.contract.ChangeEventApprovedEvent;
import com.zwinsight.common.event.outbox.OutboxEventHandler;
import com.zwinsight.common.event.outbox.OutboxMessage;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 变更事件批准 → 施工合同累计变更金额回写处理器。
 * <p>
 * 补齐 X4 横向联动断裂：当变更事件批准后，驱动施工合同的
 * {@code cumulative_change_amount} 累加，进而使产值上报额度上限
 * {@code contract_amount + cumulative_change_amount} 能够真实反映已批准的变更额。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeEventApprovedContractHandler
        implements OutboxEventHandler<ChangeEventApprovedEvent> {

    private static final String EVENT_TYPE = "CHANGE_EVENT_APPROVED";

    private final BizConstructionContractMapper contractMapper;

    @Override
    public String supports() {
        return EVENT_TYPE;
    }

    @Override
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

        Long targetContractId = event.getContractId();
        if (targetContractId == null && event.getProjectId() != null) {
            // 若变更事件未显式指定合同，自动匹配该项目下生效中的施工合同
            LambdaQueryWrapper<BizConstructionContract> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizConstructionContract::getProjectId, event.getProjectId())
                    .eq(BizConstructionContract::getStatus, "EFFECTIVE")
                    .eq(BizConstructionContract::getDeleted, 0);
            List<BizConstructionContract> activeContracts = contractMapper.selectList(wrapper);
            if (activeContracts.size() == 1) {
                targetContractId = activeContracts.get(0).getId();
                log.info("变更事件[{}]未指定合同ID，自动匹配项目下唯一生效施工合同: contractId={}",
                        event.getEventNumber(), targetContractId);
            } else if (activeContracts.size() > 1) {
                log.warn("变更事件[{}]所属项目下存在多份生效施工合同（{} 份），未指定 contractId 时不自动猜测，跳过合同回写",
                        event.getEventNumber(), activeContracts.size());
                return;
            } else {
                log.info("变更事件[{}]所属项目暂无生效施工合同，跳过合同回写", event.getEventNumber());
                return;
            }
        }

        if (targetContractId != null) {
            int rows = contractMapper.addCumulativeChangeAmount(targetContractId, costDelta);
            log.info("变更事件[{}]回写施工合同累计变更金额成功: contractId={}, delta={}, 影响行数={}",
                    event.getEventNumber(), targetContractId, costDelta, rows);
        }
    }
}
