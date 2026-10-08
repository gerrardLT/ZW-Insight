package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.zwinsight.basedata.annotation.BlacklistCheck;
import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 劳务合同服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LaborContractService {

    private final BizLaborContractMapper laborContractMapper;
    private final SerialNumberService serialNumberService;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizLaborContract> page(int page, int size, Long projectId, String contractName, String teamName, String status) {
        Page<BizLaborContract> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizLaborContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizLaborContract::getProjectId, projectId)
                .like(StrUtil.isNotBlank(contractName), BizLaborContract::getContractName, contractName)
                .like(StrUtil.isNotBlank(teamName), BizLaborContract::getTeamName, teamName)
                .eq(StrUtil.isNotBlank(status), BizLaborContract::getStatus, status)
                .orderByDesc(BizLaborContract::getCreatedAt);
        Page<BizLaborContract> result = laborContractMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存劳务合同（含编号自动生成 + 预算控制 + 供应商黑名单校验）
     */
    @BlacklistCheck
    @BudgetCheck(category = "LABOR")
    @Transactional(rollbackFor = Exception.class)
    public void save(BizLaborContract contract) {
        if (StrUtil.isBlank(contract.getContractCode())) {
            contract.setContractCode(serialNumberService.generate("LABOR_CONTRACT"));
        }
        if (contract.getCumulativeOutput() == null) {
            contract.setCumulativeOutput(BigDecimal.ZERO);
        }
        if (contract.getCumulativeSettlement() == null) {
            contract.setCumulativeSettlement(BigDecimal.ZERO);
        }
        if (contract.getCumulativePaid() == null) {
            contract.setCumulativePaid(BigDecimal.ZERO);
        }
        contract.setStatus("DRAFT");
        laborContractMapper.insert(contract);
    }

    /**
     * 提交审批
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizLaborContract contract = laborContractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("劳务合同不存在");
        }
        if (!"DRAFT".equals(contract.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("contractAmount", contract.getContractAmount());
        variables.put("projectId", contract.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "LABOR_CONTRACT", id, "labor_contract_approval", variables);

        contract.setWorkflowInstanceId(processInstanceId);
        contract.setStatus("SUBMITTED");
        laborContractMapper.updateById(contract);
    }

    /**
     * 审批通过回调（幂等：仅 SUBMITTED 态可置为 EFFECTIVE）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizLaborContract contract = laborContractMapper.selectById(id);
        if (contract == null) {
            log.warn("劳务合同审批通过回调：单据不存在, id={}", id);
            return;
        }
        if ("EFFECTIVE".equals(contract.getStatus())) {
            log.info("劳务合同已生效，跳过重复回调, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(contract.getStatus())) {
            log.warn("劳务合同当前状态非 SUBMITTED，忽略生效回调: id={}, status={}", id, contract.getStatus());
            return;
        }

        contract.setStatus("EFFECTIVE");
        laborContractMapper.updateById(contract);
        log.info("劳务合同审批通过生效: id={}, contractCode={}", id, contract.getContractCode());
    }

    /**
     * 审批驳回/撤回回调（仅 SUBMITTED 可回退 DRAFT）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizLaborContract contract = laborContractMapper.selectById(id);
        if (contract == null) {
            log.warn("劳务合同审批驳回回调：单据不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(contract.getStatus())) {
            log.warn("劳务合同当前状态非 SUBMITTED，忽略驳回回调: id={}, status={}", id, contract.getStatus());
            return;
        }

        contract.setStatus("DRAFT");
        laborContractMapper.updateById(contract);
        log.info("劳务合同审批驳回回退草稿: id={}", id);
    }

    /**
     * 根据ID查询
     */
    public BizLaborContract getById(Long id) {
        BizLaborContract contract = laborContractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("劳务合同不存在");
        }
        return contract;
    }

    /**
     * 更新劳务合同
     */
    public void update(BizLaborContract contract) {
        BizLaborContract existing = laborContractMapper.selectById(contract.getId());
        if (existing == null) {
            throw new BusinessException("劳务合同不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        // 白名单防篡改：状态与累计字段不可由客户端写入
        contract.setStatus(null);
        contract.setCumulativeOutput(null);
        contract.setCumulativeSettlement(null);
        contract.setCumulativePaid(null);
        contract.setWorkflowInstanceId(null);
        laborContractMapper.updateById(contract);
    }

    /**
     * 删除劳务合同
     */
    public void delete(Long id) {
        BizLaborContract existing = laborContractMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("劳务合同不存在");
        }
        if (!"DRAFT".equals(existing.getStatus()) && !E2eTestGuard.containsE2eTestMarker(existing)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        laborContractMapper.deleteById(id);
    }
}
