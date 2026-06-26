package com.zwinsight.material.event;

import com.zwinsight.material.service.MaterialRefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退货退款事件监听器
 * <p>
 * 监听 MaterialReturnCreatedEvent 事件，当退货出库单关联了采购合同时，
 * 自动创建退款申请记录。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialReturnRefundEventListener {

    private final MaterialRefundService refundService;

    /**
     * 处理退货出库事件 - 自动生成退款申请
     * <p>
     * 仅当 contractId 不为 null 时才生成退款申请，
     * 未关联采购合同的退货出库仅做库存扣减，不产生退款。
     * </p>
     */
    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onMaterialReturn(MaterialReturnCreatedEvent event) {
        if (event.getContractId() == null) {
            log.debug("退货出库单[{}]未关联采购合同，跳过退款申请生成", event.getOutboundId());
            return;
        }

        log.info("退货出库单[{}]关联采购合同[{}]，开始生成退款申请",
                event.getOutboundId(), event.getContractId());

        Long refundId = refundService.createRefundFromReturn(event);

        log.info("退款申请创建成功，退款单ID={}", refundId);
    }
}
