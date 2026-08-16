package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizPersonalReimbursement;
import com.zwinsight.finance.mapper.BizPersonalReimbursementMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 个人报销服务
 */
@Service
@RequiredArgsConstructor
public class PersonalReimbursementService {

    private final BizPersonalReimbursementMapper personalReimbursementMapper;
    private final ApprovalService approvalService;

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
     */
    public Long save(BizPersonalReimbursement reimbursement) {
        reimbursement.setStatus("DRAFT");
        personalReimbursementMapper.insert(reimbursement);
        return reimbursement.getId();
    }

    /**
     * 提交个人报销
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizPersonalReimbursement reimbursement = personalReimbursementMapper.selectById(id);
        if (reimbursement == null) {
            throw new BusinessException("个人报销不存在");
        }
        if (!"DRAFT".equals(reimbursement.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
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
        reimbursement.setStatus("APPROVED");
        personalReimbursementMapper.updateById(reimbursement);
    }
}
