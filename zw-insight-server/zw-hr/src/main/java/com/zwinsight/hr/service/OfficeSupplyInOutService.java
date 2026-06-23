package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizOfficeSupply;
import com.zwinsight.hr.domain.BizOfficeSupplyInOut;
import com.zwinsight.hr.mapper.BizOfficeSupplyInOutMapper;
import com.zwinsight.hr.mapper.BizOfficeSupplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 办公用品出入库服务
 */
@Service
@RequiredArgsConstructor
public class OfficeSupplyInOutService {

    private final BizOfficeSupplyInOutMapper inOutMapper;
    private final BizOfficeSupplyMapper supplyMapper;

    /**
     * 分页查询
     */
    public PageResult<BizOfficeSupplyInOut> page(int page, int size, Long supplyId, String ioType) {
        Page<BizOfficeSupplyInOut> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizOfficeSupplyInOut> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(supplyId != null, BizOfficeSupplyInOut::getSupplyId, supplyId)
                .eq(ioType != null && !ioType.isEmpty(), BizOfficeSupplyInOut::getIoType, ioType)
                .orderByDesc(BizOfficeSupplyInOut::getCreatedAt);
        Page<BizOfficeSupplyInOut> result = inOutMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增出入库记录
     */
    public void save(BizOfficeSupplyInOut inOut) {
        inOut.setStatus("DRAFT");
        inOutMapper.insert(inOut);
    }

    /**
     * 提交出入库（IN→库存增，OUT→库存减）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizOfficeSupplyInOut inOut = inOutMapper.selectById(id);
        if (inOut == null) {
            throw new BusinessException("出入库记录不存在");
        }
        if (!"DRAFT".equals(inOut.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        BizOfficeSupply supply = supplyMapper.selectById(inOut.getSupplyId());
        if (supply == null) {
            throw new BusinessException("关联办公用品不存在");
        }

        int currentStock = supply.getStockQuantity() == null ? 0 : supply.getStockQuantity();

        if ("IN".equals(inOut.getIoType())) {
            supply.setStockQuantity(currentStock + inOut.getQuantity());
        } else if ("OUT".equals(inOut.getIoType())) {
            if (currentStock < inOut.getQuantity()) {
                throw new BusinessException("库存不足，当前库存：" + currentStock);
            }
            supply.setStockQuantity(currentStock - inOut.getQuantity());
        }

        supplyMapper.updateById(supply);
        inOut.setStatus("APPROVED");
        inOutMapper.updateById(inOut);
    }
}
