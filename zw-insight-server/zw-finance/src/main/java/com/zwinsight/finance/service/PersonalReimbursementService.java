package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizPersonalReimbursement;
import com.zwinsight.finance.domain.BizReimbursementDetail;
import com.zwinsight.finance.mapper.BizPersonalReimbursementMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 个人报销服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalReimbursementService {

    private final BizPersonalReimbursementMapper personalReimbursementMapper;
    private final ApprovalService approvalService;
    private final ReimbursementDetailService reimbursementDetailService;

    /**
     * 分页查询
     */
    public PageResult<BizPersonalReimbursement> page(int page, int size) {
        Page<BizPersonalReimbursement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPersonalReimbursement> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BizPersonalReimbursement::getCreatedAt);
        Page<BizPersonalReimbursement> result = personalReimbursementMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增个人报销（返回新记录 id，供移动端链式调用 submit 完成两段式提交）
     * <p>携带 details 时级联保存费用科目明细（V2026_60）；本表无 projectId 列，
     * 项目相关招待费需由明细的 entertainment.projectId 显式携带项目归属。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(BizPersonalReimbursement reimbursement) {
        reimbursement.setStatus("DRAFT");
        personalReimbursementMapper.insert(reimbursement);
        reimbursementDetailService.saveDetails(
                BizReimbursementDetail.SOURCE_PERSONAL,
                reimbursement.getId(),
                null,
                reimbursement.getDetails(),
                reimbursement.getTotalAmount());
        return reimbursement.getId();
    }

    /**
     * 提交个人报销（发起审批，状态置 SUBMITTED）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizPersonalReimbursement reimbursement = personalReimbursementMapper.selectById(id);
        if (reimbursement == null) {
            throw new BusinessException("个人报销不存在");
        }
        if (!"DRAFT".equals(reimbursement.getStatus()) && !"REJECTED".equals(reimbursement.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可提交");
        }
        // P0 修复（FIN-PRB-04，2026-08-12）：报销金额必须>0，原实现负/零无校验直接生效
        if (reimbursement.getTotalAmount() == null || reimbursement.getTotalAmount().signum() <= 0) {
            throw new BusinessException("报销金额必须大于0");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("totalAmount", reimbursement.getTotalAmount());
        String processInstanceId = approvalService.startProcess(
                "PERSONAL_REIMBURSEMENT", id, "personal_reimbursement_approval", variables);

        reimbursement.setWorkflowInstanceId(processInstanceId);
        reimbursement.setStatus("SUBMITTED");
        personalReimbursementMapper.updateById(reimbursement);
    }

    /**
     * 审批通过回调：置 APPROVED
     * <p>幂等：状态已为 APPROVED 时直接返回（兼容审批时点改造前的存量在途单据与重复事件）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizPersonalReimbursement reimbursement = personalReimbursementMapper.selectById(id);
        if (reimbursement == null) {
            log.warn("个人报销审批通过回调：记录不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(reimbursement.getStatus())) {
            log.info("个人报销已生效，跳过重复回调, id={}", id);
            return;
        }
        reimbursement.setStatus("APPROVED");
        personalReimbursementMapper.updateById(reimbursement);
        log.info("个人报销审批通过并生效, id={}, totalAmount={}", id, reimbursement.getTotalAmount());
    }

    /**
     * 审批驳回/撤回回调：状态置 REJECTED（数据未生效，无需回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizPersonalReimbursement reimbursement = personalReimbursementMapper.selectById(id);
        if (reimbursement == null) {
            log.warn("个人报销驳回回调：记录不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(reimbursement.getStatus())) {
            return;
        }
        reimbursement.setStatus("REJECTED");
        personalReimbursementMapper.updateById(reimbursement);
        log.info("个人报销审批驳回, id={}", id);
    }
}
