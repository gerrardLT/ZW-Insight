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
        supplyMapper.updateById(supply);
    }

    /**
     * 删除办公用品
     */
    public void delete(Long id) {
        supplyMapper.deleteById(id);
    }
}
