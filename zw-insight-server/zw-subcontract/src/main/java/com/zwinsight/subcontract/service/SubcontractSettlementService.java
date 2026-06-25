package com.zwinsight.subcontract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.subcontract.domain.BizSubcontract;
import com.zwinsight.subcontract.domain.BizSubcontractSettlement;
import com.zwinsight.subcontract.mapper.BizSubcontractMapper;
import com.zwinsight.subcontract.mapper.BizSubcontractSettlementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 分包结算服务
 */
@Service
@RequiredArgsConstructor
public class SubcontractSettlementService {

    private final BizSubcontractSettlementMapper settlementMapper;
    private final BizSubcontractMapper subcontractMapper;
    private final BizProjectMapper projectMapper;

    public PageResult<BizSubcontractSettlement> page(int page, int size, Long projectId, Long contractId) {
        Page<BizSubcontractSettlement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSubcontractSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizSubcontractSettlement::getProjectId, projectId)
                .eq(contractId != null, BizSubcontractSettlement::getContractId, contractId)
                .orderByDesc(BizSubcontractSettlement::getCreatedAt);
        return PageResult.of(settlementMapper.selectPage(pageParam, wrapper));
    }

    public void save(BizSubcontractSettlement settlement) {
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);
    }

    public BizSubcontractSettlement getById(Long id) {
        BizSubcontractSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) throw new BusinessException("结算记录不存在");
        return settlement;
    }

    public void update(BizSubcontractSettlement settlement) {
        BizSubcontractSettlement existing = settlementMapper.selectById(settlement.getId());
        if (existing == null) throw new BusinessException("结算记录不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        settlementMapper.updateById(settlement);
    }

    public void delete(Long id) {
        BizSubcontractSettlement existing = settlementMapper.selectById(id);
        if (existing == null) throw new BusinessException("结算记录不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        settlementMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizSubcontractSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) throw new BusinessException("结算记录不存在");
        if (!"DRAFT".equals(settlement.getStatus())) throw new BusinessException("仅草稿状态可提交");

        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        // 回写合同累计结算
        BizSubcontract contract = subcontractMapper.selectById(settlement.getContractId());
        if (contract != null) {
            BigDecimal cumulative = contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
            contract.setCumulativeSettlement(cumulative.add(settlement.getSettlementAmount()));
            subcontractMapper.updateById(contract);
        }

        // 回写项目总支出
        BizProject project = projectMapper.selectById(settlement.getProjectId());
        if (project != null) {
            BigDecimal totalExpense = project.getTotalExpense() != null ? project.getTotalExpense() : BigDecimal.ZERO;
            project.setTotalExpense(totalExpense.add(settlement.getSettlementAmount()));
            projectMapper.updateById(project);
        }
    }
}
