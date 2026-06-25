package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.domain.BizLaborSettlement;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.labor.mapper.BizLaborSettlementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 劳务结算服务
 */
@Service
@RequiredArgsConstructor
public class LaborSettlementService {

    private final BizLaborSettlementMapper settlementMapper;
    private final BizLaborContractMapper laborContractMapper;

    /**
     * 分页查询
     */
    public PageResult<BizLaborSettlement> page(int page, int size, Long projectId, Long contractId) {
        Page<BizLaborSettlement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizLaborSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizLaborSettlement::getProjectId, projectId)
                .eq(contractId != null, BizLaborSettlement::getContractId, contractId)
                .orderByDesc(BizLaborSettlement::getCreatedAt);
        Page<BizLaborSettlement> result = settlementMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存结算
     */
    public void save(BizLaborSettlement settlement) {
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);
    }

    /**
     * 根据ID查询
     */
    public BizLaborSettlement getById(Long id) {
        BizLaborSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("结算记录不存在");
        }
        return settlement;
    }

    /**
     * 更新结算
     */
    public void update(BizLaborSettlement settlement) {
        BizLaborSettlement existing = settlementMapper.selectById(settlement.getId());
        if (existing == null) {
            throw new BusinessException("结算记录不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        settlementMapper.updateById(settlement);
    }

    /**
     * 删除结算
     */
    public void delete(Long id) {
        BizLaborSettlement existing = settlementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("结算记录不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        settlementMapper.deleteById(id);
    }

    /**
     * 提交（回写合同累计结算）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizLaborSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("结算记录不存在");
        }
        if (!"DRAFT".equals(settlement.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        // 回写合同累计结算
        BizLaborContract contract = laborContractMapper.selectById(settlement.getContractId());
        if (contract != null) {
            BigDecimal cumulative = contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
            contract.setCumulativeSettlement(cumulative.add(settlement.getSettlementAmount()));
            laborContractMapper.updateById(contract);
        }
    }
}
