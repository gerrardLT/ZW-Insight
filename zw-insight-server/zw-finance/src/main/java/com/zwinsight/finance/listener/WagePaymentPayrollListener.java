package com.zwinsight.finance.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.labor.LaborPayrollApprovedEvent;
import com.zwinsight.finance.domain.BizWagePaymentDetail;
import com.zwinsight.finance.mapper.BizWagePaymentDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 劳务工资单审批通过监听器（财务域）
 * <p>
 * 补齐 X2 横向联动断裂：当劳务工资单审批通过时，
 * 自动生成农民工工资专户待代发台账（PENDING），
 * 将劳务核算的工资单与财务专户的总包代发打通，
 * 满足国务院令第724号关于总包代发可追溯、与结算单据闭环的法定要求。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WagePaymentPayrollListener {

    private final BizWagePaymentDetailMapper wagePaymentDetailMapper;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onLaborPayrollApproved(LaborPayrollApprovedEvent event) {
        if (event == null || event.getPayrollId() == null) {
            return;
        }

        // 幂等防重：同一工资单只生成一条待代发记录
        LambdaQueryWrapper<BizWagePaymentDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizWagePaymentDetail::getPayrollId, event.getPayrollId())
                .eq(BizWagePaymentDetail::getDeleted, 0);
        if (wagePaymentDetailMapper.selectCount(wrapper) > 0) {
            log.info("劳务工资单[{}]已存在代发明细，跳过重复生成", event.getPayrollId());
            return;
        }

        BigDecimal amount = event.getTotalAmount() != null ? event.getTotalAmount() : BigDecimal.ZERO;
        if (amount.signum() <= 0) {
            log.info("劳务工资单[{}]金额为零，不生成代发明细", event.getPayrollId());
            return;
        }

        BizWagePaymentDetail detail = new BizWagePaymentDetail();
        detail.setProjectId(event.getProjectId());
        detail.setPayrollId(event.getPayrollId());
        detail.setTeamId(event.getTeamId());
        detail.setPeriodStart(event.getPeriodStart());
        detail.setPeriodEnd(event.getPeriodEnd());
        detail.setAmount(amount);
        detail.setPayStatus(BizWagePaymentDetail.STATUS_PENDING);
        detail.setRemark("由劳务工资单#" + event.getPayrollId() + "审批通过自动生成");

        wagePaymentDetailMapper.insert(detail);
        log.info("劳务工资单[{}]审批通过，自动生成待代发明细成功, detailId={}, amount={}",
                event.getPayrollId(), detail.getId(), amount);
    }
}
