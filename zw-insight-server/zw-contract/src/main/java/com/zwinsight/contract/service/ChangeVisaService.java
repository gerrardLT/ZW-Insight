package com.zwinsight.contract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizChangeVisa;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizChangeVisaMapper;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.workflow.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 变更签证服务
 */
@Service
@RequiredArgsConstructor
public class ChangeVisaService {

    private final BizChangeVisaMapper changeVisaMapper;
    private final BizConstructionContractMapper contractMapper;
    private final ApprovalService approvalService;

    /**
     * 分页查询
     */
    public PageResult<BizChangeVisa> page(int page, int size, Long projectId, Long contractId, String changeType) {
        Page<BizChangeVisa> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizChangeVisa> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizChangeVisa::getProjectId, projectId)
                .eq(contractId != null, BizChangeVisa::getContractId, contractId)
                .eq(StrUtil.isNotBlank(changeType), BizChangeVisa::getChangeType, changeType)
                .orderByDesc(BizChangeVisa::getCreatedAt);
        Page<BizChangeVisa> result = changeVisaMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增变更签证
     */
    public void save(BizChangeVisa changeVisa) {
        changeVisa.setStatus("DRAFT");
        changeVisaMapper.insert(changeVisa);
    }

    /**
     * 提交审批（审批通过→回写合同累计变更金额）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizChangeVisa changeVisa = changeVisaMapper.selectById(id);
        if (changeVisa == null) {
            throw new BusinessException("变更签证不存在");
        }
        if (!"DRAFT".equals(changeVisa.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        // 发起审批流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("changeAmount", changeVisa.getChangeAmount());
        variables.put("contractId", changeVisa.getContractId());
        String processInstanceId = approvalService.startProcess(
                "CHANGE_VISA", id, "change_visa_approval", variables);

        changeVisa.setWorkflowInstanceId(processInstanceId);
        changeVisa.setStatus("APPROVED");
        changeVisaMapper.updateById(changeVisa);

        // 回写合同累计变更金额
        BizConstructionContract contract = contractMapper.selectById(changeVisa.getContractId());
        if (contract != null) {
            BigDecimal cumulative = contract.getCumulativeChangeAmount() == null
                    ? BigDecimal.ZERO : contract.getCumulativeChangeAmount();
            contract.setCumulativeChangeAmount(cumulative.add(changeVisa.getChangeAmount()));
            contractMapper.updateById(contract);
        }
    }
}
