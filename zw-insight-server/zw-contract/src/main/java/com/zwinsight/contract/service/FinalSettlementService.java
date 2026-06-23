package com.zwinsight.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizFinalSettlement;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizFinalSettlementMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 竣工结算服务
 */
@Service
@RequiredArgsConstructor
public class FinalSettlementService {

    private final BizFinalSettlementMapper settlementMapper;
    private final BizConstructionContractMapper contractMapper;
    private final BizProjectMapper projectMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizFinalSettlement> page(int page, int size, Long projectId) {
        Page<BizFinalSettlement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizFinalSettlement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizFinalSettlement::getProjectId, projectId)
                .orderByDesc(BizFinalSettlement::getCreatedAt);
        Page<BizFinalSettlement> result = settlementMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存竣工结算（草稿）
     */
    public void save(BizFinalSettlement settlement) {
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);
    }

    /**
     * 提交审批（审批通过→更新合同状态为SETTLED，回写项目结算金额）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizFinalSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("竣工结算不存在");
        }
        if (!"DRAFT".equals(settlement.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("settlementAmount", settlement.getSettlementAmount());
        variables.put("projectId", settlement.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "FINAL_SETTLEMENT", id, "final_settlement_approval", variables);

        settlement.setWorkflowInstanceId(processInstanceId);
        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        // 更新合同状态为 SETTLED
        BizConstructionContract contract = contractMapper.selectById(settlement.getContractId());
        if (contract != null) {
            contract.setStatus("SETTLED");
            contractMapper.updateById(contract);
        }

        // 回写项目结算金额
        BizProject project = projectMapper.selectById(settlement.getProjectId());
        if (project != null) {
            BigDecimal currentSettlement = project.getSettlementAmount() != null ? project.getSettlementAmount() : BigDecimal.ZERO;
            project.setSettlementAmount(currentSettlement.add(settlement.getSettlementAmount()));
            projectMapper.updateById(project);
        }
    }
}
