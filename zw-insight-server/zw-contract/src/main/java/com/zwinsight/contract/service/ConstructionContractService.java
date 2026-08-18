package com.zwinsight.contract.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.E2eTestGuard;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizContractDetail;
import com.zwinsight.contract.domain.dto.ContractCreateRequest;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.mapper.BizContractDetailMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import com.zwinsight.file.service.SerialNumberService;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 施工合同服务
 */
@Service
@RequiredArgsConstructor
public class ConstructionContractService {

    private final BizConstructionContractMapper contractMapper;
    private final BizContractDetailMapper detailMapper;
    private final SerialNumberService serialNumberService;
    private final ApprovalService approvalService;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询
     */
    public PageResult<BizConstructionContract> page(int page, int size, Long projectId, String status) {
        Page<BizConstructionContract> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizConstructionContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizConstructionContract::getProjectId, projectId)
                .eq(StrUtil.isNotBlank(status), BizConstructionContract::getStatus, status)
                .orderByDesc(BizConstructionContract::getCreatedAt);
        Page<BizConstructionContract> result = contractMapper.selectPage(pageParam, wrapper);
        // 回填项目名称：实体仅可靠持久化 projectId，列表需展示 projectName
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizConstructionContract::getProjectId, BizConstructionContract::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BizConstructionContract getById(Long id) {
        BizConstructionContract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        return contract;
    }

    /**
     * 从请求 DTO 创建合同
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFromRequest(ContractCreateRequest request) {
        BizConstructionContract contract = new BizConstructionContract();
        BeanUtil.copyProperties(request, contract);
        save(contract);
    }

    /**
     * 从请求 DTO 更新合同
     */
    public void updateFromRequest(Long id, ContractCreateRequest request) {
        BizConstructionContract contract = new BizConstructionContract();
        BeanUtil.copyProperties(request, contract);
        contract.setId(id);
        update(contract);
    }

    /**
     * 新增合同（自动编号 + 税金计算）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizConstructionContract contract) {
        // 自动生成合同编号
        String contractCode = serialNumberService.generate("CONTRACT");
        contract.setContractCode(contractCode);
        contract.setStatus("DRAFT");

        // 税金计算: amountWithoutTax = contractAmount / (1 + taxRate/100)
        calculateTax(contract);

        // 初始化累计字段
        if (contract.getCumulativeChangeAmount() == null) {
            contract.setCumulativeChangeAmount(BigDecimal.ZERO);
        }
        if (contract.getCumulativeOutput() == null) {
            contract.setCumulativeOutput(BigDecimal.ZERO);
        }
        if (contract.getCumulativeInvoiceAmount() == null) {
            contract.setCumulativeInvoiceAmount(BigDecimal.ZERO);
        }
        if (contract.getCumulativeReceivedAmount() == null) {
            contract.setCumulativeReceivedAmount(BigDecimal.ZERO);
        }

        contractMapper.insert(contract);
    }

    /**
     * 更新合同
     */
    public void update(BizConstructionContract contract) {
        BizConstructionContract existing = contractMapper.selectById(contract.getId());
        if (existing == null) {
            throw new BusinessException("合同不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        // 重新计算税金
        calculateTax(contract);
        contractMapper.updateById(contract);
    }

    /**
     * 提交审批（启动流程，状态置 SUBMITTED；审批通过后由监听器回调 {@link #onApproved(Long)} 生效）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizConstructionContract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        if (!"DRAFT".equals(contract.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("contractAmount", contract.getContractAmount());
        variables.put("projectId", contract.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "CONSTRUCTION_CONTRACT", id, "construction_contract_approval", variables);

        // 更新流程实例ID和状态（审批通过后才置 EFFECTIVE 并回写项目）
        contract.setWorkflowInstanceId(processInstanceId);
        contract.setStatus("SUBMITTED");
        contractMapper.updateById(contract);
    }

    /**
     * 审批通过回调：合同置 EFFECTIVE + 回写项目累计合同金额（原子累加）+ 项目状态流转 CONSTRUCTION
     * <p>幂等：状态已为 EFFECTIVE 时直接返回（兼容存量在途单据与重复事件）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizConstructionContract contract = contractMapper.selectById(id);
        if (contract == null) {
            return;
        }
        if ("EFFECTIVE".equals(contract.getStatus())) {
            return;
        }

        contract.setStatus("EFFECTIVE");
        contractMapper.updateById(contract);

        // 回写项目累计合同金额（原子累加）+ 项目状态流转
        if (contract.getProjectId() != null) {
            BigDecimal amount = contract.getContractAmount() == null
                    ? BigDecimal.ZERO : contract.getContractAmount();
            projectMapper.addContractAmount(contract.getProjectId(), amount);

            BizProject project = projectMapper.selectById(contract.getProjectId());
            if (project != null) {
                // 项目状态流转：中标/已报备 → 施工中（施工合同生效触发）
                advanceToConstruction(project);
                projectMapper.updateById(project);
            }
        }
    }

    /**
     * 审批驳回/撤回回调：回退草稿可修改重提（数据未生效，无需回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizConstructionContract contract = contractMapper.selectById(id);
        if (contract == null || !"SUBMITTED".equals(contract.getStatus())) {
            return;
        }
        contract.setStatus("DRAFT");
        contractMapper.updateById(contract);
    }

    /**
     * 项目状态流转 WON/FILED → CONSTRUCTION（施工合同生效时调用）
     */
    private void advanceToConstruction(BizProject project) {
        if ("WON".equals(project.getStatus()) || "FILED".equals(project.getStatus())) {
            project.setStatus("CONSTRUCTION");
        }
    }

    /**
     * 获取合同明细
     */
    public List<BizContractDetail> getDetails(Long contractId) {
        LambdaQueryWrapper<BizContractDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizContractDetail::getContractId, contractId)
                .orderByAsc(BizContractDetail::getSortOrder);
        return detailMapper.selectList(wrapper);
    }

    /**
     * 删除合同
     */
    public void delete(Long id) {
        BizConstructionContract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        if (!"DRAFT".equals(contract.getStatus()) && !E2eTestGuard.containsE2eTestMarker(contract)) {
            throw new BusinessException("仅草稿状态可删除");
        }
        contractMapper.deleteById(id);
    }

    /**
     * 保存合同明细（先删后增）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDetails(Long contractId, List<BizContractDetail> details) {
        // 删除原有明细
        detailMapper.delete(
                new LambdaQueryWrapper<BizContractDetail>().eq(BizContractDetail::getContractId, contractId));
        // 批量插入新明细
        if (details != null && !details.isEmpty()) {
            int sortOrder = 1;
            for (BizContractDetail detail : details) {
                detail.setContractId(contractId);
                detail.setSortOrder(sortOrder++);
                detailMapper.insert(detail);
            }
        }
    }

    /**
     * 税金计算
     * amountWithoutTax = contractAmount / (1 + taxRate/100)
     * taxAmount = contractAmount - amountWithoutTax
     */
    private void calculateTax(BizConstructionContract contract) {
        if (contract.getContractAmount() != null && contract.getTaxRate() != null) {
            BigDecimal rate = contract.getTaxRate().divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            BigDecimal divisor = BigDecimal.ONE.add(rate);
            BigDecimal amountWithoutTax = contract.getContractAmount()
                    .divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = contract.getContractAmount().subtract(amountWithoutTax);

            contract.setAmountWithoutTax(amountWithoutTax);
            contract.setTaxAmount(taxAmount);
        }
    }
}
