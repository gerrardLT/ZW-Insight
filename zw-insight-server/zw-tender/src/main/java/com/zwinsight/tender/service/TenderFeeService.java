package com.zwinsight.tender.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.tender.domain.BizTenderFee;
import com.zwinsight.tender.mapper.BizTenderFeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 投标费用服务
 */
@Service
@RequiredArgsConstructor
public class TenderFeeService {

    private final BizTenderFeeMapper feeMapper;

    /**
     * 分页查询
     */
    public PageResult<BizTenderFee> page(int page, int size, Long registerId) {
        Page<BizTenderFee> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizTenderFee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(registerId != null, BizTenderFee::getRegisterId, registerId)
                .orderByDesc(BizTenderFee::getCreatedAt);
        Page<BizTenderFee> result = feeMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增费用
     */
    public void save(BizTenderFee fee) {
        fee.setStatus("DRAFT");
        feeMapper.insert(fee);
    }

    /**
     * 更新费用
     */
    public void update(BizTenderFee fee) {
        BizTenderFee existing = feeMapper.selectById(fee.getId());
        if (existing == null) throw new BusinessException("投标费用不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        feeMapper.updateById(fee);
    }

    /**
     * 删除费用
     */
    public void delete(Long id) {
        BizTenderFee existing = feeMapper.selectById(id);
        if (existing == null) throw new BusinessException("投标费用不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        feeMapper.deleteById(id);
    }

    /**
     * 确认付款（上传回单）
     */
    public void confirmPayment(Long id, String receiptFile) {
        BizTenderFee fee = feeMapper.selectById(id);
        if (fee == null) {
            throw new BusinessException("投标费用不存在");
        }
        fee.setStatus("PAID");
        fee.setReceiptFile(receiptFile);
        feeMapper.updateById(fee);
    }
}
