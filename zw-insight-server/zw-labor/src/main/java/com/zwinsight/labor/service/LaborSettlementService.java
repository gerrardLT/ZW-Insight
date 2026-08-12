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
        // P1 修复（2026-08-12，批次二取证枚举）：防 PUT 体携带 status 直接落库绕过 submit
        settlement.setStatus(null);
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

        // P1 修复（2026-08-12，批次二取证枚举）：结算金额 null 时后续 add 抛 NPE（500），
        // 负数可回退合同累计结算；对齐财务模块金额>0 口径
        if (settlement.getSettlementAmount() == null
                || settlement.getSettlementAmount().signum() <= 0) {
            throw new BusinessException("结算金额必须大于0");
        }

        // B5 修复（2026-08-11，对齐分包口径）：累计结算金额不能超过合同金额，
        // 原实现无守卫可超合同结算
        BizLaborContract contract = laborContractMapper.selectById(settlement.getContractId());
        if (contract != null) {
            BigDecimal contractAmount = contract.getContractAmount() != null ? contract.getContractAmount() : BigDecimal.ZERO;
            BigDecimal currentCumulative = contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
            BigDecimal newCumulative = currentCumulative.add(settlement.getSettlementAmount());
            if (newCumulative.compareTo(contractAmount) > 0) {
                BigDecimal maxSettlement = contractAmount.subtract(currentCumulative);
                throw new BusinessException("结算金额超出合同金额限制，当前最大可结算金额：" + maxSettlement);
            }
        }

        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        // 回写合同累计结算
        if (contract != null) {
            BigDecimal cumulative = contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
            contract.setCumulativeSettlement(cumulative.add(settlement.getSettlementAmount()));
            laborContractMapper.updateById(contract);
        }
    }
}
