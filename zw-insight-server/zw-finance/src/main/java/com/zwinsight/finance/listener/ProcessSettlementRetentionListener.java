package com.zwinsight.finance.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.event.contract.ProcessSettlementApprovedEvent;
import com.zwinsight.contract.domain.BizContractPayTerm;
import com.zwinsight.contract.service.ContractPayTermService;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 过程结算生效 → 质保金自动计提监听器（V2026_109，C2）。
 * <p>
 * 依据财建〔2022〕183 号（合同约定保留不超过工程价款总额 3% 的质量保证金）：
 * 计提额以结算单的 {@code retentionDeduct}（本期实际扣留额）为准——条款比例仅作
 * 表单阶段建议，不在此重算覆盖业务事实。质保期月数取合同 RETENTION 条款；
 * 合同无条款时期限字段留空由人工补录，不猜默认值。
 * </p>
 * <p>
 * 幂等：(source_type=PROCESS_SETTLEMENT, source_id=settlementId) DB 唯一 guard
 * （生成列 active_retention_source），监听器先查后插双保险。
 * 同步 {@code @EventListener} 在结算审批事务内执行，失败整体回滚，不吞异常。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessSettlementRetentionListener {

    private final ContractPayTermService payTermService;
    private final BizRetentionMoneyMapper retentionMapper;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onProcessSettlementApproved(ProcessSettlementApprovedEvent event) {
        BigDecimal deduct = event.getRetentionDeduct();
        if (deduct == null || deduct.signum() <= 0) {
            return; // 本期未扣留质保金：不计提（条款建议已在结算表单阶段提供）
        }
        Long exists = retentionMapper.selectCount(new LambdaQueryWrapper<BizRetentionMoney>()
                .eq(BizRetentionMoney::getSourceType, "PROCESS_SETTLEMENT")
                .eq(BizRetentionMoney::getSourceId, event.getSettlementId()));
        if (exists != null && exists > 0) {
            log.info("过程结算质保金计提已存在，跳过, settlementId={}", event.getSettlementId());
            return;
        }
        BizContractPayTerm term = payTermService.firstActiveTerm(
                BizContractPayTerm.CATEGORY_CONSTRUCTION, event.getContractId(), BizContractPayTerm.TYPE_RETENTION);
        BizRetentionMoney retention = new BizRetentionMoney();
        retention.setProjectId(event.getProjectId());
        retention.setContractId(event.getContractId());
        retention.setRetentionAmount(deduct);
        retention.setReturnedAmount(BigDecimal.ZERO);
        retention.setStatus("ACTIVE");
        retention.setSourceType("PROCESS_SETTLEMENT");
        retention.setSourceId(event.getSettlementId());
        LocalDate basis = event.getPeriodEnd() != null ? event.getPeriodEnd()
                : (event.getSettlementDate() != null ? event.getSettlementDate() : LocalDate.now());
        retention.setStartDate(basis);
        if (term != null) {
            retention.setRetentionRate(term.getRatio());
            if (term.getRetentionMonths() != null && term.getRetentionMonths() > 0) {
                retention.setRetentionPeriod(term.getRetentionMonths());
                retention.setExpireDate(basis.plusMonths(term.getRetentionMonths()));
            }
        }
        retentionMapper.insert(retention);
        log.info("过程结算计提质保金, settlementId={}, contractId={}, amount={}, expireDate={}",
                event.getSettlementId(), event.getContractId(), deduct, retention.getExpireDate());
    }
}
