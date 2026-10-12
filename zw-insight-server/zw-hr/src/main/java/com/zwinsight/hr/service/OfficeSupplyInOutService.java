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

import java.math.BigDecimal;
import java.time.LocalDate;

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
     * <p>2026-10-12 修复：原先只维护 {@code stock_quantity}，而档案页展示的
     * {@code current_stock/total_inbound/total_issued/last_inbound_date} 四列**无任何写入方**、恒为 0。
     * 现按 IN/OUT 同步维护：入库累计 total_inbound + last_inbound_date，出库累计 total_issued，
     * 且 current_stock 始终等于 stock_quantity。</p>
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
        if (inOut.getQuantity() == null || inOut.getQuantity() <= 0) {
            throw new BusinessException("出入库数量必须大于0");
        }

        BizOfficeSupply supply = supplyMapper.selectById(inOut.getSupplyId());
        if (supply == null) {
            throw new BusinessException("关联办公用品不存在");
        }

        int currentStock = supply.getStockQuantity() == null ? 0 : supply.getStockQuantity();
        BigDecimal quantity = BigDecimal.valueOf(inOut.getQuantity());

        if ("IN".equals(inOut.getIoType())) {
            supply.setStockQuantity(currentStock + inOut.getQuantity());
            supply.setTotalInbound(nvl(supply.getTotalInbound()).add(quantity));
            supply.setLastInboundDate(LocalDate.now());
        } else if ("OUT".equals(inOut.getIoType())) {
            if (currentStock < inOut.getQuantity()) {
                throw new BusinessException("库存不足，当前库存：" + currentStock);
            }
            supply.setStockQuantity(currentStock - inOut.getQuantity());
            supply.setTotalIssued(nvl(supply.getTotalIssued()).add(quantity));
        } else {
            throw new BusinessException("出入库类型无效：" + inOut.getIoType());
        }

        // current_stock 与 stock_quantity 保持一致（档案页读的是 current_stock）
        supply.setCurrentStock(BigDecimal.valueOf(
                supply.getStockQuantity() == null ? 0 : supply.getStockQuantity()));

        supplyMapper.updateById(supply);
        inOut.setStatus("APPROVED");
        inOutMapper.updateById(inOut);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
