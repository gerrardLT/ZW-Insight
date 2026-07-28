package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.zwinsight.basedata.annotation.BlacklistCheck;
import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 劳务合同服务
 */
@Service
@RequiredArgsConstructor
public class LaborContractService {

    private final BizLaborContractMapper laborContractMapper;

    /**
     * 分页查询
     */
    public PageResult<BizLaborContract> page(int page, int size, Long projectId, String contractName, String teamName, String status) {
        Page<BizLaborContract> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizLaborContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizLaborContract::getProjectId, projectId)
                .like(StrUtil.isNotBlank(contractName), BizLaborContract::getContractName, contractName)
                .like(StrUtil.isNotBlank(teamName), BizLaborContract::getTeamName, teamName)
                .eq(StrUtil.isNotBlank(status), BizLaborContract::getStatus, status)
                .orderByDesc(BizLaborContract::getCreatedAt);
        Page<BizLaborContract> result = laborContractMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存劳务合同（含预算控制 + 供应商黑名单校验）
     */
    @BlacklistCheck
    @BudgetCheck(category = "LABOR")
    @Transactional(rollbackFor = Exception.class)
    public void save(BizLaborContract contract) {
        if (contract.getCumulativeSettlement() == null) {
            contract.setCumulativeSettlement(BigDecimal.ZERO);
        }
        if (contract.getCumulativePaid() == null) {
            contract.setCumulativePaid(BigDecimal.ZERO);
        }
        contract.setStatus("DRAFT");
        laborContractMapper.insert(contract);
    }

    /**
     * 提交审批
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizLaborContract contract = laborContractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("劳务合同不存在");
        }
        if (!"DRAFT".equals(contract.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }
        contract.setStatus("EFFECTIVE");
        laborContractMapper.updateById(contract);
    }

    /**
     * 根据ID查询
     */
    public BizLaborContract getById(Long id) {
        BizLaborContract contract = laborContractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("劳务合同不存在");
        }
        return contract;
    }

    /**
     * 更新劳务合同
     */
    public void update(BizLaborContract contract) {
        BizLaborContract existing = laborContractMapper.selectById(contract.getId());
        if (existing == null) {
            throw new BusinessException("劳务合同不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        laborContractMapper.updateById(contract);
    }

    /**
     * 删除劳务合同
     */
    public void delete(Long id) {
        BizLaborContract existing = laborContractMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("劳务合同不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        laborContractMapper.deleteById(id);
    }
}
