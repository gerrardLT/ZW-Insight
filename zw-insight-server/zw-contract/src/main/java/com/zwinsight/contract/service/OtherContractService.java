package com.zwinsight.contract.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 其他合同服务
 */
@Service
@RequiredArgsConstructor
public class OtherContractService {

    private final BizOtherContractMapper otherContractMapper;

    /**
     * 分页查询
     */
    public PageResult<BizOtherContract> page(int page, int size, Long projectId, String contractCategory) {
        Page<BizOtherContract> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizOtherContract> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizOtherContract::getProjectId, projectId)
                .eq(StrUtil.isNotBlank(contractCategory), BizOtherContract::getContractCategory, contractCategory)
                .orderByDesc(BizOtherContract::getCreatedAt);
        Page<BizOtherContract> result = otherContractMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BizOtherContract getById(Long id) {
        BizOtherContract contract = otherContractMapper.selectById(id);
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        return contract;
    }

    /**
     * 新增其他合同
     */
    public void save(BizOtherContract contract) {
        contract.setStatus("DRAFT");
        // 初始化累计字段
        if (contract.getCumulativeInvoice() == null) {
            contract.setCumulativeInvoice(BigDecimal.ZERO);
        }
        if (contract.getCumulativeReceived() == null) {
            contract.setCumulativeReceived(BigDecimal.ZERO);
        }
        if (contract.getCumulativeSettlement() == null) {
            contract.setCumulativeSettlement(BigDecimal.ZERO);
        }
        if (contract.getCumulativePaid() == null) {
            contract.setCumulativePaid(BigDecimal.ZERO);
        }
        otherContractMapper.insert(contract);
    }

    /**
     * 更新其他合同
     */
    public void update(BizOtherContract contract) {
        BizOtherContract existing = otherContractMapper.selectById(contract.getId());
        if (existing == null) {
            throw new BusinessException("合同不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        otherContractMapper.updateById(contract);
    }

    /**
     * 删除其他合同
     */
    public void delete(Long id) {
        BizOtherContract existing = otherContractMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("合同不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        otherContractMapper.deleteById(id);
    }
}
