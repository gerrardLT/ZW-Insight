package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizSealApply;
import com.zwinsight.hr.mapper.BizSealApplyMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 用印申请服务
 */
@Service
@RequiredArgsConstructor
public class SealApplyService {

    private final BizSealApplyMapper sealApplyMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizSealApply> page(int page, int size) {
        Page<BizSealApply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSealApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(BizSealApply::getCreatedAt);
        Page<BizSealApply> result = sealApplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增用印申请
     */
    public void save(BizSealApply apply) {
        apply.setStatus("DRAFT");
        sealApplyMapper.insert(apply);
    }

    /**
     * 提交用印申请
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizSealApply apply = sealApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("用印申请不存在");
        }
        if (!"DRAFT".equals(apply.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("sealType", apply.getSealType());
        variables.put("applicant", apply.getApplicant());
        String processInstanceId = approvalService.startProcess(
                "SEAL_APPLY", id, "seal_apply_approval", variables);

        apply.setStatus("APPROVED");
        sealApplyMapper.updateById(apply);
    }
}
