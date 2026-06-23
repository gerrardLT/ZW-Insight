package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
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
 * 付款申请服务
 */
@Service
@RequiredArgsConstructor
public class PaymentApplyService {

    private final BizPaymentApplyMapper paymentApplyMapper;
    private final BizOtherContractMapper otherContractMapper;
    private final BizProjectMapper projectMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizPaymentApply> page(int page, int size, Long projectId, Long contractId) {
        Page<BizPaymentApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPaymentApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizPaymentApply::getProjectId, projectId)
                .eq(contractId != null, BizPaymentApply::getContractId, contractId)
                .orderByDesc(BizPaymentApply::getCreatedAt);
        Page<BizPaymentApply> result = paymentApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增付款申请
     */
    public void save(BizPaymentApply paymentApply) {
        paymentApply.setStatus("DRAFT");
        paymentApplyMapper.insert(paymentApply);
    }

    /**
     * 提交付款申请（校验paymentAmount≤累计结算-已付，审批通过回写项目totalExpense+合同cumulativePaid）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizPaymentApply paymentApply = paymentApplyMapper.selectById(id);
        if (paymentApply == null) {
            throw new BusinessException("付款申请不存在");
        }
        if (!"DRAFT".equals(paymentApply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 校验付款金额
        BizOtherContract contract = otherContractMapper.selectById(paymentApply.getContractId());
        if (contract == null) {
            throw new BusinessException("关联合同不存在");
        }

        BigDecimal cumulativeSettlement = contract.getCumulativeSettlement() == null
                ? BigDecimal.ZERO : contract.getCumulativeSettlement();
        BigDecimal cumulativePaid = contract.getCumulativePaid() == null
                ? BigDecimal.ZERO : contract.getCumulativePaid();
        BigDecimal maxPayment = cumulativeSettlement.subtract(cumulativePaid);

        if (paymentApply.getPaymentAmount().compareTo(maxPayment) > 0) {
            throw new BusinessException("付款金额不能超过累计结算减已付金额，最大可付金额：" + maxPayment);
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("paymentAmount", paymentApply.getPaymentAmount());
        variables.put("contractId", paymentApply.getContractId());
        String processInstanceId = approvalService.startProcess(
                "PAYMENT_APPLY", id, "payment_apply_approval", variables);

        paymentApply.setWorkflowInstanceId(processInstanceId);
        paymentApply.setStatus("APPROVED");
        paymentApplyMapper.updateById(paymentApply);

        // 回写合同累计已付
        contract.setCumulativePaid(cumulativePaid.add(paymentApply.getPaymentAmount()));
        otherContractMapper.updateById(contract);

        // 回写项目总支出
        BizProject project = projectMapper.selectById(paymentApply.getProjectId());
        if (project != null) {
            BigDecimal totalExpense = project.getTotalExpense() == null
                    ? BigDecimal.ZERO : project.getTotalExpense();
            project.setTotalExpense(totalExpense.add(paymentApply.getPaymentAmount()));
            projectMapper.updateById(project);
        }
    }
}
