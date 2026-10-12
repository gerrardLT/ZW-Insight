package com.zwinsight.hr.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizOfficeSupply;
import com.zwinsight.hr.mapper.BizOfficeSupplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 办公用品服务
 */
@Service
@RequiredArgsConstructor
public class OfficeSupplyService {

    private final BizOfficeSupplyMapper supplyMapper;

    /**
     * 分页查询
     */
    public PageResult<BizOfficeSupply> page(int page, int size, String supplyName) {
        Page<BizOfficeSupply> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizOfficeSupply> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(supplyName), BizOfficeSupply::getSupplyName, supplyName)
                .orderByDesc(BizOfficeSupply::getCreatedAt);
        Page<BizOfficeSupply> result = supplyMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 新增办公用品
     */
    public void save(BizOfficeSupply supply) {
        if (supply.getStockQuantity() == null) {
            supply.setStockQuantity(0);
        }
        // 档案页读的是 current_stock，与 stock_quantity 双列并存 → 建档时即对齐，避免分叉
        if (supply.getCurrentStock() == null) {
            supply.setCurrentStock(BigDecimal.valueOf(supply.getStockQuantity()));
        }
        supplyMapper.insert(supply);
    }

    /**
     * 更新办公用品
     */
    public void update(BizOfficeSupply supply) {
        BizOfficeSupply existing = supplyMapper.selectById(supply.getId());
        if (existing == null) {
            throw new BusinessException("办公用品不存在");
        }
        // 直接 PUT stockQuantity 时同步 current_stock（2026-10-12：两列此前会分叉，
        // 档案页显示旧值、HR 页显示新值）
        if (supply.getStockQuantity() != null) {
            supply.setCurrentStock(BigDecimal.valueOf(supply.getStockQuantity()));
        }
        supplyMapper.updateById(supply);
    }

    /**
     * 删除办公用品
     */
    public void delete(Long id) {
        supplyMapper.deleteById(id);
    }
}
