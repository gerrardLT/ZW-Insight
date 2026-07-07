package com.zwinsight.finance.service;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.dto.InvoiceApplyCreateRequest;
import com.zwinsight.finance.mapper.BizInvoiceApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 开票申请服务
 */
@Service
@RequiredArgsConstructor
public class InvoiceApplyService {

    private final BizInvoiceApplyMapper invoiceApplyMapper;
    private final BizConstructionContractMapper contractMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizInvoiceApply> page(int page, int size, Long projectId, Long contractId) {
        Page<BizInvoiceApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInvoiceApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizInvoiceApply::getProjectId, projectId)
                .eq(contractId != null, BizInvoiceApply::getContractId, contractId)
                .orderByDesc(BizInvoiceApply::getCreatedAt);
        Page<BizInvoiceApply> result = invoiceApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 从请求 DTO 创建开票申请
     */
    public void saveFromRequest(InvoiceApplyCreateRequest request) {
        BizInvoiceApply invoiceApply = new BizInvoiceApply();
        BeanUtil.copyProperties(request, invoiceApply);
        save(invoiceApply);
    }

    /**
     * 从请求 DTO 更新开票申请
     */
    public void updateFromRequest(Long id, InvoiceApplyCreateRequest request) {
        BizInvoiceApply invoiceApply = new BizInvoiceApply();
        BeanUtil.copyProperties(request, invoiceApply);
        invoiceApply.setId(id);
        update(invoiceApply);
    }

    /**
     * 新增开票申请
     */
    public void save(BizInvoiceApply invoiceApply) {
        invoiceApply.setStatus("DRAFT");
        invoiceApplyMapper.insert(invoiceApply);
    }

    /**
     * 根据ID查询
     */
    public BizInvoiceApply getById(Long id) {
        BizInvoiceApply invoiceApply = invoiceApplyMapper.selectById(id);
        if (invoiceApply == null) {
            throw new BusinessException("开票申请不存在");
        }
        return invoiceApply;
    }

    /**
     * 更新开票申请
     */
    public void update(BizInvoiceApply invoiceApply) {
        BizInvoiceApply existing = invoiceApplyMapper.selectById(invoiceApply.getId());
        if (existing == null) {
            throw new BusinessException("开票申请不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        invoiceApplyMapper.updateById(invoiceApply);
    }

    /**
     * 删除开票申请
     */
    public void delete(Long id) {
        BizInvoiceApply existing = invoiceApplyMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("开票申请不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        invoiceApplyMapper.deleteById(id);
    }

    /**
     * 提交开票申请（校验invoiceAmount≤累计产值-已开票，审批通过回写合同cumulativeInvoiceAmount）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizInvoiceApply invoiceApply = invoiceApplyMapper.selectById(id);
        if (invoiceApply == null) {
            throw new BusinessException("开票申请不存在");
        }
        if (!"DRAFT".equals(invoiceApply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 校验开票金额
        BizConstructionContract contract = contractMapper.selectById(invoiceApply.getContractId());
        if (contract == null) {
            throw new BusinessException("关联合同不存在");
        }

        BigDecimal cumulativeOutput = contract.getCumulativeOutput() == null
                ? BigDecimal.ZERO : contract.getCumulativeOutput();
        BigDecimal cumulativeInvoiced = contract.getCumulativeInvoiceAmount() == null
                ? BigDecimal.ZERO : contract.getCumulativeInvoiceAmount();
        BigDecimal maxInvoiceAmount = cumulativeOutput.subtract(cumulativeInvoiced);

        if (invoiceApply.getInvoiceAmount().compareTo(maxInvoiceAmount) > 0) {
            throw new BusinessException("开票金额不能超过累计产值减已开票金额，最大可开票金额：" + maxInvoiceAmount);
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("invoiceAmount", invoiceApply.getInvoiceAmount());
        variables.put("contractId", invoiceApply.getContractId());
        String processInstanceId = approvalService.startProcess(
                "INVOICE_APPLY", id, "invoice_apply_approval", variables);

        invoiceApply.setWorkflowInstanceId(processInstanceId);
        invoiceApply.setStatus("APPROVED");
        invoiceApplyMapper.updateById(invoiceApply);

        // 回写合同累计开票金额
        contract.setCumulativeInvoiceAmount(cumulativeInvoiced.add(invoiceApply.getInvoiceAmount()));
        contractMapper.updateById(contract);
    }
}
