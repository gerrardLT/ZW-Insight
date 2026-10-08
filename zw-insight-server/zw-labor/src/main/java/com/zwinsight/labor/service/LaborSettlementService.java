package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.domain.BizLaborSettlement;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.labor.mapper.BizLaborSettlementMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 劳务结算服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaborSettlementService {

    private final BizLaborSettlementMapper settlementMapper;
    private final BizLaborContractMapper laborContractMapper;
    private final ApprovalService approvalService;

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
        if (settlement.getSettlementAmount() == null || settlement.getSettlementAmount().signum() <= 0) {
            throw new BusinessException("结算金额必须大于0");
        }
        settlement.setStatus("DRAFT");
        settlement.setWorkflowInstanceId(null);
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
        if (settlement.getSettlementAmount() != null && settlement.getSettlementAmount().signum() <= 0) {
            throw new BusinessException("结算金额必须大于0");
        }
        existing.setSettlementAmount(settlement.getSettlementAmount());
        existing.setCumulativeSettlement(settlement.getCumulativeSettlement());
        if (settlement.getProjectId() != null) {
            existing.setProjectId(settlement.getProjectId());
        }
        if (settlement.getContractId() != null) {
            existing.setContractId(settlement.getContractId());
        }
        settlementMapper.updateById(existing);
    }

    /**
     * 删除结算
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizLaborSettlement existing = settlementMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("结算记录不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        BigDecimal amount = existing.getSettlementAmount() == null
                ? BigDecimal.ZERO : existing.getSettlementAmount();
        settlementMapper.deleteById(id);

        if ("APPROVED".equals(existing.getStatus()) && existing.getContractId() != null) {
            laborContractMapper.addSettlement(existing.getContractId(), amount.negate());
            log.info("劳务结算删除并冲销合同累计结算值, id={}, amount={}", id, amount);
        }
    }

    /**
     * 提交审批
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

        if (settlement.getSettlementAmount() == null
                || settlement.getSettlementAmount().signum() <= 0) {
            throw new BusinessException("结算金额必须大于0");
        }

        // 累计结算不能超过合同总金额守卫
        BizLaborContract contract = laborContractMapper.selectById(settlement.getContractId());
        if (contract != null && contract.getContractAmount() != null) {
            BigDecimal currentCumulative = contract.getCumulativeSettlement() != null ? contract.getCumulativeSettlement() : BigDecimal.ZERO;
            BigDecimal newCumulative = currentCumulative.add(settlement.getSettlementAmount());
            if (newCumulative.compareTo(contract.getContractAmount()) > 0) {
                BigDecimal maxSettlement = contract.getContractAmount().subtract(currentCumulative);
                throw new BusinessException("结算金额超出合同金额限制，当前最大可结算金额：" + maxSettlement);
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("settlementAmount", settlement.getSettlementAmount());
        variables.put("projectId", settlement.getProjectId());
        variables.put("contractId", settlement.getContractId());
        String processInstanceId = approvalService.startProcess(
                "LABOR_SETTLEMENT", id, "labor_settlement_approval", variables);

        settlement.setWorkflowInstanceId(processInstanceId);
        settlement.setStatus("SUBMITTED");
        settlementMapper.updateById(settlement);
    }

    /**
     * 审批通过回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizLaborSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            log.warn("劳务结算审批通过回调：单据不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(settlement.getStatus())) {
            log.info("劳务结算已生效，跳过重复回调, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(settlement.getStatus())) {
            log.warn("劳务结算当前状态非 SUBMITTED，忽略生效回调: id={}, status={}", id, settlement.getStatus());
            return;
        }

        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        if (settlement.getContractId() != null && settlement.getSettlementAmount() != null) {
            laborContractMapper.addSettlement(settlement.getContractId(), settlement.getSettlementAmount());
            log.info("劳务结算审批通过并原子回写合同累计结算值: settlementId={}, contractId={}, amount={}",
                    id, settlement.getContractId(), settlement.getSettlementAmount());
        }
    }

    /**
     * 审批驳回回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizLaborSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            log.warn("劳务结算审批驳回回调：单据不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(settlement.getStatus())) {
            log.warn("劳务结算当前状态非 SUBMITTED，忽略驳回回调: id={}, status={}", id, settlement.getStatus());
            return;
        }

        settlement.setStatus("DRAFT");
        settlementMapper.updateById(settlement);
        log.info("劳务结算审批驳回回退草稿: id={}", id);
    }
}
