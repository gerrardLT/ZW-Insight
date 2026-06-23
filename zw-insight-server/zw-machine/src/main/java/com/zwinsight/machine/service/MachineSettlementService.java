package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineContract;
import com.zwinsight.machine.domain.BizMachineSettlement;
import com.zwinsight.machine.mapper.BizMachineContractMapper;
import com.zwinsight.machine.mapper.BizMachineSettlementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 机械结算服务
 */
@Service
@RequiredArgsConstructor
public class MachineSettlementService {

    private final BizMachineSettlementMapper settlementMapper;
    private final BizMachineContractMapper machineContractMapper;

    public PageResult<BizMachineSettlement> page(int page, int size, Long projectId, Long contractId) {
        Page<BizMachineSettlement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMachineSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizMachineSettlement::getProjectId, projectId)
                .eq(contractId != null, BizMachineSettlement::getContractId, contractId)
                .orderByDesc(BizMachineSettlement::getCreatedAt);
        return PageResult.of(settlementMapper.selectPage(pageParam, wrapper));
    }

    public void save(BizMachineSettlement settlement) {
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizMachineSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) throw new BusinessException("结算记录不存在");
        if (!"DRAFT".equals(settlement.getStatus())) throw new BusinessException("仅草稿状态可提交");

        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        // 回写合同累计结算
        BizMachineContract contract = machineContractMapper.selectById(settlement.getContractId());
        if (contract != null) {
            BigDecimal cumulative = contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
            contract.setCumulativeSettlement(cumulative.add(settlement.getSettlementAmount()));
            machineContractMapper.updateById(contract);
        }
    }
}
