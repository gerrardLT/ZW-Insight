package com.zwinsight.finance.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.event.UrgeNotifyEvent;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizInvoiceApply;
import com.zwinsight.finance.domain.dto.InvoiceApplyCreateRequest;
import com.zwinsight.finance.mapper.BizInvoiceApplyMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 开票申请服务
 * <p>
 * 审批后生效模式：submit 仅校验并启动流程（状态 SUBMITTED），
 * 审批通过后由 InvoiceApplyApprovalListener 回调 {@link #onApproved(Long)} 回写合同累计开票金额。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceApplyService {

    private final BizInvoiceApplyMapper invoiceApplyMapper;
    private final BizConstructionContractMapper contractMapper;
    private final BizProjectMapper projectMapper;
    private final ApprovalService approvalService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 分页查询
     */
    public PageResult<BizInvoiceApply> page(int page, int size, Long projectId, Long contractId, String status) {
        Page<BizInvoiceApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInvoiceApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizInvoiceApply::getProjectId, projectId)
                .eq(contractId != null, BizInvoiceApply::getContractId, contractId)
                .eq(StrUtil.isNotBlank(status), BizInvoiceApply::getStatus, status)
                .orderByDesc(BizInvoiceApply::getCreatedAt);
        Page<BizInvoiceApply> result = invoiceApplyMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizInvoiceApply::getProjectId, BizInvoiceApply::setProjectName);
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
     * 提交开票申请（校验invoiceAmount≤累计产值-已开票后启动流程，状态置 SUBMITTED，不回写累计数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizInvoiceApply invoiceApply = invoiceApplyMapper.selectById(id);
        if (invoiceApply == null) {
            throw new BusinessException("开票申请不存在");
        }
        if (!"DRAFT".equals(invoiceApply.getStatus()) && !"REJECTED".equals(invoiceApply.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可提交");
        }

        // 校验开票金额
        BizConstructionContract contract = contractMapper.selectById(invoiceApply.getContractId());
        if (contract == null) {
            throw new BusinessException("关联合同不存在");
        }
        validateInvoiceLimit(contract, invoiceApply.getInvoiceAmount());

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("invoiceAmount", invoiceApply.getInvoiceAmount());
        variables.put("contractId", invoiceApply.getContractId());
        String processInstanceId = approvalService.startProcess(
                "INVOICE_APPLY", id, "invoice_apply_approval", variables);

        invoiceApply.setWorkflowInstanceId(processInstanceId);
        invoiceApply.setStatus("SUBMITTED");
        invoiceApplyMapper.updateById(invoiceApply);
    }

    /**
     * 审批通过回调：回写合同累计开票金额（SQL 原子累加）
     * <p>幂等：状态已为 APPROVED 时直接返回（兼容存量在途单据与重复事件）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizInvoiceApply invoiceApply = invoiceApplyMapper.selectById(id);
        if (invoiceApply == null) {
            log.warn("开票申请审批通过回调：申请不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(invoiceApply.getStatus())) {
            log.info("开票申请已生效，跳过重复回调, id={}", id);
            return;
        }

        BizConstructionContract contract = contractMapper.selectById(invoiceApply.getContractId());
        if (contract == null) {
            log.error("开票申请审批通过回调：关联合同不存在, id={}, contractId={}", id, invoiceApply.getContractId());
            return;
        }

        // 审批期间上限可能变化，生效前重新校验；不通过则置 REJECTED 并通知发起人
        try {
            validateInvoiceLimit(contract, invoiceApply.getInvoiceAmount());
        } catch (BusinessException e) {
            invoiceApply.setStatus("REJECTED");
            invoiceApplyMapper.updateById(invoiceApply);
            notifyInitiator(invoiceApply.getCreatedBy(), "开票申请生效失败", e.getMessage(), invoiceApply.getWorkflowInstanceId());
            log.warn("开票申请生效校验失败已驳回, id={}, reason={}", id, e.getMessage());
            return;
        }

        invoiceApply.setStatus("APPROVED");
        invoiceApplyMapper.updateById(invoiceApply);

        // 回写合同累计开票金额（原子累加）
        contractMapper.addCumulativeInvoiceAmount(invoiceApply.getContractId(), invoiceApply.getInvoiceAmount());

        log.info("开票申请审批通过并生效, id={}, invoiceAmount={}", id, invoiceApply.getInvoiceAmount());
    }

    /**
     * 审批驳回/撤回回调：状态置 REJECTED（数据未生效，无需回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizInvoiceApply invoiceApply = invoiceApplyMapper.selectById(id);
        if (invoiceApply == null) {
            log.warn("开票申请驳回回调：申请不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(invoiceApply.getStatus())) {
            return;
        }
        invoiceApply.setStatus("REJECTED");
        invoiceApplyMapper.updateById(invoiceApply);
        log.info("开票申请审批驳回, id={}", id);
    }

    /**
     * 校验开票金额不超过累计产值减已开票金额
     */
    private void validateInvoiceLimit(BizConstructionContract contract, BigDecimal invoiceAmount) {
        BigDecimal cumulativeOutput = contract.getCumulativeOutput() == null
                ? BigDecimal.ZERO : contract.getCumulativeOutput();
        BigDecimal cumulativeInvoiced = contract.getCumulativeInvoiceAmount() == null
                ? BigDecimal.ZERO : contract.getCumulativeInvoiceAmount();
        BigDecimal maxInvoiceAmount = cumulativeOutput.subtract(cumulativeInvoiced);

        if (invoiceAmount.compareTo(maxInvoiceAmount) > 0) {
            throw new BusinessException("开票金额不能超过累计产值减已开票金额，最大可开票金额：" + maxInvoiceAmount);
        }
    }

    /**
     * 站内信通知发起人（生效失败等异常场景，不静默）
     */
    private void notifyInitiator(Long userId, String title, String content, String workflowInstanceId) {
        if (userId == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(new UrgeNotifyEvent(this, userId, title, content, workflowInstanceId, null));
        } catch (Exception e) {
            log.error("发送开票申请通知失败, workflowInstanceId={}", workflowInstanceId, e);
        }
    }
}
