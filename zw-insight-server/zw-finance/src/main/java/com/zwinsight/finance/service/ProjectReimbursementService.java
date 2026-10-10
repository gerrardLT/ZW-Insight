package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizProjectReimbursement;
import com.zwinsight.finance.domain.BizReimbursementDetail;
import com.zwinsight.finance.domain.BizReserveFundApply;
import com.zwinsight.finance.mapper.BizProjectReimbursementMapper;
import com.zwinsight.finance.mapper.BizReserveFundApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 项目报销服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectReimbursementService {

    private final BizProjectReimbursementMapper reimbursementMapper;
    private final BizReserveFundApplyMapper reserveFundApplyMapper;
    private final ApprovalService approvalService;
    private final ReimbursementDetailService reimbursementDetailService;

    /**
     * 分页查询
     */
    public PageResult<BizProjectReimbursement> page(int page, int size, Long projectId) {
        Page<BizProjectReimbursement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizProjectReimbursement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizProjectReimbursement::getProjectId, projectId)
                .orderByDesc(BizProjectReimbursement::getCreatedAt);
        Page<BizProjectReimbursement> result = reimbursementMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增项目报销（携带 details 时级联保存费用科目明细，V2026_60）
     * <p>主表与明细同事务：明细合计与总额不一致、科目无效、招待费专项缺失均整体回滚。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(BizProjectReimbursement reimbursement) {
        reimbursement.setStatus("DRAFT");
        reimbursementMapper.insert(reimbursement);
        reimbursementDetailService.saveDetails(
                BizReimbursementDetail.SOURCE_PROJECT,
                reimbursement.getId(),
                reimbursement.getProjectId(),
                reimbursement.getDetails(),
                reimbursement.getTotalAmount());
        return reimbursement.getId();
    }

    /**
     * 提交项目报销（发起审批，状态置 SUBMITTED；冲抵备用金待审批通过后生效）
     * <p>冲抵参数在提交时即校验，避免审批通过时才发现不可冲抵而卡住流程。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizProjectReimbursement reimbursement = reimbursementMapper.selectById(id);
        if (reimbursement == null) {
            throw new BusinessException("报销记录不存在");
        }
        if (!"DRAFT".equals(reimbursement.getStatus()) && !"REJECTED".equals(reimbursement.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可提交");
        }
        // P0 修复（FIN-PRJ-06，2026-08-12）：报销金额必须>0，原实现负/零无校验直接生效
        if (reimbursement.getTotalAmount() == null || reimbursement.getTotalAmount().signum() <= 0) {
            throw new BusinessException("报销金额必须大于0");
        }
        // P0 修复（FIN-PRJ-07，2026-08-12）：冲抵额必须≥0、不超报销额、不超备用金待冲抵余额
        validateOffset(reimbursement);

        // 发起审批
        Map<String, Object> variables = new HashMap<>();
        variables.put("totalAmount", reimbursement.getTotalAmount());
        variables.put("projectId", reimbursement.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "PROJECT_REIMBURSEMENT", id, "project_reimbursement_approval", variables);

        reimbursement.setWorkflowInstanceId(processInstanceId);
        reimbursement.setStatus("SUBMITTED");
        reimbursementMapper.updateById(reimbursement);
    }

    /**
     * 审批通过回调：置 APPROVED 并冲抵备用金
     * <p>幂等：状态已为 APPROVED 时直接返回（兼容审批时点改造前的存量在途单据与重复事件）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizProjectReimbursement reimbursement = reimbursementMapper.selectById(id);
        if (reimbursement == null) {
            log.warn("项目报销审批通过回调：记录不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(reimbursement.getStatus())) {
            log.info("项目报销已生效，跳过重复回调, id={}", id);
            return;
        }

        // 审批期间备用金待冲抵余额可能已被其他单据占用，生效前重新校验
        applyReserveOffset(reimbursement);

        reimbursement.setStatus("APPROVED");
        reimbursementMapper.updateById(reimbursement);
        log.info("项目报销审批通过并生效, id={}, totalAmount={}", id, reimbursement.getTotalAmount());
    }

    /**
     * 审批驳回/撤回回调：状态置 REJECTED（数据未生效，无需回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizProjectReimbursement reimbursement = reimbursementMapper.selectById(id);
        if (reimbursement == null) {
            log.warn("项目报销驳回回调：记录不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(reimbursement.getStatus())) {
            return;
        }
        reimbursement.setStatus("REJECTED");
        reimbursementMapper.updateById(reimbursement);
        log.info("项目报销审批驳回, id={}", id);
    }

    /**
     * 校验冲抵参数（不改数据）：未开启冲抵、未关联备用金申请、申请已不存在时均视为无需冲抵。
     */
    private void validateOffset(BizProjectReimbursement reimbursement) {
        BizReserveFundApply reserveApply = loadReserveApply(reimbursement);
        if (reserveApply != null) {
            validateOffsetAgainst(reimbursement, reserveApply);
        }
    }

    /**
     * 校验并累加备用金已冲抵金额。
     */
    private void applyReserveOffset(BizProjectReimbursement reimbursement) {
        BizReserveFundApply reserveApply = loadReserveApply(reimbursement);
        if (reserveApply == null) {
            return;
        }
        validateOffsetAgainst(reimbursement, reserveApply);
        BigDecimal currentOffset = reserveApply.getOffsetAmount() == null
                ? BigDecimal.ZERO : reserveApply.getOffsetAmount();
        BigDecimal offsetAmount = reimbursement.getOffsetAmount() == null
                ? BigDecimal.ZERO : reimbursement.getOffsetAmount();
        reserveApply.setOffsetAmount(currentOffset.add(offsetAmount));
        reserveFundApplyMapper.updateById(reserveApply);
    }

    /** 未开启冲抵、未关联备用金申请或申请已不存在时返回 null（视为无需冲抵） */
    private BizReserveFundApply loadReserveApply(BizProjectReimbursement reimbursement) {
        if (reimbursement.getOffsetReserve() == null || reimbursement.getOffsetReserve() != 1
                || reimbursement.getReserveApplyId() == null) {
            return null;
        }
        return reserveFundApplyMapper.selectById(reimbursement.getReserveApplyId());
    }

    /** 冲抵额必须≥0、不超报销额、且不超备用金待冲抵余额 */
    private void validateOffsetAgainst(BizProjectReimbursement reimbursement, BizReserveFundApply reserveApply) {
        BigDecimal offsetAmount = reimbursement.getOffsetAmount() == null
                ? BigDecimal.ZERO : reimbursement.getOffsetAmount();
        if (offsetAmount.signum() < 0) {
            throw new BusinessException("冲抵金额不能为负数");
        }
        if (offsetAmount.compareTo(reimbursement.getTotalAmount()) > 0) {
            throw new BusinessException("冲抵金额不能超过报销金额");
        }
        BigDecimal pendingOffset = pendingOffset(reserveApply);
        if (offsetAmount.compareTo(pendingOffset) > 0) {
            throw new BusinessException("冲抵金额超过备用金待冲抵余额：" + pendingOffset);
        }
    }

    /** 备用金待冲抵余额 = 申请金额 − 已归还 − 已冲抵 */
    private BigDecimal pendingOffset(BizReserveFundApply reserveApply) {
        BigDecimal applyAmount = reserveApply.getApplyAmount() == null
                ? BigDecimal.ZERO : reserveApply.getApplyAmount();
        BigDecimal returnedAmount = reserveApply.getReturnedAmount() == null
                ? BigDecimal.ZERO : reserveApply.getReturnedAmount();
        BigDecimal currentOffset = reserveApply.getOffsetAmount() == null
                ? BigDecimal.ZERO : reserveApply.getOffsetAmount();
        return applyAmount.subtract(returnedAmount).subtract(currentOffset);
    }
}
