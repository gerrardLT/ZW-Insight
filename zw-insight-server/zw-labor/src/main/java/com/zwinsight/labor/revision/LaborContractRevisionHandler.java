package com.zwinsight.labor.revision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.revision.ExpenseContractRevisionHandler;
import com.zwinsight.common.revision.RevisionContract;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 劳务合同修订处理器（V2026_123）：修订下限取已发生产值/结算/收票/付款的最大值。
 */
@Component
@RequiredArgsConstructor
public class LaborContractRevisionHandler implements ExpenseContractRevisionHandler {

    private final BizLaborContractMapper mapper;
    private final ObjectMapper json;

    @Override
    public String category() {
        return "LABOR";
    }

    @Override
    public RevisionContract lockAndValidate(Long contractId, Integer baseVersion, BigDecimal afterAmount) {
        // 行锁：与施工合同路径一致，避免并发创建两张在途修订
        BizLaborContract contract = mapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizLaborContract>()
                        .eq(BizLaborContract::getId, contractId)
                        .last("FOR UPDATE"));
        if (contract == null) throw new BusinessException("合同不存在");
        if (!"EFFECTIVE".equals(contract.getStatus())) throw new BusinessException("仅生效合同可申请修订");
        if (contract.getContractAmount() == null) throw new BusinessException("合同金额缺失，无法修订");
        if (!Objects.equals(contract.getVersion(), baseVersion)) throw new BusinessException("合同版本已变化，请刷新后申请");
        if (afterAmount == null || afterAmount.signum() <= 0) throw new BusinessException("修订后金额必须大于0");
        if (afterAmount.compareTo(contract.getContractAmount()) == 0) throw new BusinessException("修订金额未变化");
        BigDecimal occurred = max(contract.getCumulativeOutput(), contract.getCumulativeSettlement(),
                contract.getCumulativeInvoiceReceived(), contract.getCumulativePaid());
        if (afterAmount.compareTo(occurred) < 0) {
            throw new BusinessException("修订后合同总额不得低于已发生产值、结算、收票或付款");
        }
        return RevisionContract.builder()
                .contractId(contract.getId())
                .projectId(contract.getProjectId())
                .version(contract.getVersion())
                .contractAmount(contract.getContractAmount())
                .taxRate(contract.getTaxRate())
                .snapshot(snapshot(contract))
                .build();
    }

    @Override
    public void applyAmount(Long contractId, Integer baseVersion, BigDecimal beforeAmount, BigDecimal afterAmount) {
        BizLaborContract contract = mapper.selectById(contractId);
        if (contract == null) throw new BusinessException("合同不存在");
        if (!Objects.equals(contract.getVersion(), baseVersion)
                || contract.getContractAmount() == null
                || contract.getContractAmount().compareTo(beforeAmount) != 0) {
            throw new BusinessException("合同基准已变化，须重新申请修订");
        }
        contract.setContractAmount(afterAmount);
        tax(contract);
        if (mapper.updateById(contract) != 1) throw new BusinessException("合同并发修订冲突");
    }

    @Override
    public boolean exists(Long contractId) {
        return mapper.selectById(contractId) != null;
    }

    private void tax(BizLaborContract contract) {
        BigDecimal rate = contract.getTaxRate();
        if (rate == null) {
            // 税率未知时无法派生税额：置 0（不含税=含税、税额 0），避免 updateById 跳过 null 残留旧值
            contract.setAmountWithoutTax(contract.getContractAmount());
            contract.setTaxAmount(BigDecimal.ZERO);
            return;
        }
        BigDecimal net = contract.getContractAmount()
                .divide(BigDecimal.ONE.add(rate.movePointLeft(2)), 2, RoundingMode.HALF_UP);
        contract.setAmountWithoutTax(net);
        contract.setTaxAmount(contract.getContractAmount().subtract(net));
    }

    private static BigDecimal max(BigDecimal... values) {
        BigDecimal max = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(max) > 0) max = value;
        }
        return max;
    }

    private String snapshot(BizLaborContract contract) {
        try {
            return json.writeValueAsString(contract);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("合同快照序列化失败", e);
        }
    }
}
