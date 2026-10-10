package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizReserveFundApply;
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
 * 备用金申请服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReserveFundApplyService {

    private final BizReserveFundApplyMapper reserveFundApplyMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询（支持按项目与状态过滤；移动端备用金归还需按 status=APPROVED 拉未还清申请）
     */
    public PageResult<BizReserveFundApply> page(int page, int size, Long projectId, String status) {
        Page<BizReserveFundApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizReserveFundApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizReserveFundApply::getProjectId, projectId)
                .eq(status != null && !status.isEmpty(), BizReserveFundApply::getStatus, status)
                .orderByDesc(BizReserveFundApply::getCreatedAt);
        Page<BizReserveFundApply> result = reserveFundApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增备用金申请（返回新记录 id，供移动端链式调用 submit 完成两段式提交）
     */
    public Long save(BizReserveFundApply apply) {
        // P0 修复（FIN-RFA-04，2026-08-12）：申请金额必须>0，原实现负/零无校验
        if (apply.getApplyAmount() == null || apply.getApplyAmount().signum() <= 0) {
            throw new BusinessException("备用金申请金额必须大于0");
        }
        apply.setStatus("DRAFT");
        if (apply.getReturnedAmount() == null) {
            apply.setReturnedAmount(BigDecimal.ZERO);
        }
        if (apply.getOffsetAmount() == null) {
            apply.setOffsetAmount(BigDecimal.ZERO);
        }
        reserveFundApplyMapper.insert(apply);
        return apply.getId();
    }

    /**
     * 提交备用金申请（发起审批，状态置 SUBMITTED；审批通过后才会出现在归还页的待归还列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizReserveFundApply apply = reserveFundApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("备用金申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus()) && !"REJECTED".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿或已驳回状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("applyAmount", apply.getApplyAmount());
        variables.put("projectId", apply.getProjectId());
        String processInstanceId = approvalService.startProcess(
                "RESERVE_FUND_APPLY", id, "reserve_fund_apply_approval", variables);

        apply.setWorkflowInstanceId(processInstanceId);
        apply.setStatus("SUBMITTED");
        reserveFundApplyMapper.updateById(apply);
    }

    /**
     * 审批通过回调：置 APPROVED
     * <p>幂等：状态已为 APPROVED 时直接返回（兼容审批时点改造前的存量在途单据与重复事件）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long id) {
        BizReserveFundApply apply = reserveFundApplyMapper.selectById(id);
        if (apply == null) {
            log.warn("备用金申请审批通过回调：记录不存在, id={}", id);
            return;
        }
        if ("APPROVED".equals(apply.getStatus())) {
            log.info("备用金申请已生效，跳过重复回调, id={}", id);
            return;
        }
        apply.setStatus("APPROVED");
        reserveFundApplyMapper.updateById(apply);
        log.info("备用金申请审批通过并生效, id={}, applyAmount={}", id, apply.getApplyAmount());
    }

    /**
     * 审批驳回/撤回回调：状态置 REJECTED（数据未生效，无需回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long id) {
        BizReserveFundApply apply = reserveFundApplyMapper.selectById(id);
        if (apply == null) {
            log.warn("备用金申请驳回回调：记录不存在, id={}", id);
            return;
        }
        if (!"SUBMITTED".equals(apply.getStatus())) {
            return;
        }
        apply.setStatus("REJECTED");
        reserveFundApplyMapper.updateById(apply);
        log.info("备用金申请审批驳回, id={}", id);
    }
}
