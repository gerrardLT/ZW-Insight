package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizCostSubcategory;
import com.zwinsight.budget.mapper.BizCostSubcategoryMapper;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 费用子类服务
 */
@Service
@RequiredArgsConstructor
public class CostSubcategoryService {

    private final BizCostSubcategoryMapper costSubcategoryMapper;

    /**
     * 按费用类别查询子类列表
     */
    public List<BizCostSubcategory> list(String costCategory) {
        LambdaQueryWrapper<BizCostSubcategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(costCategory != null, BizCostSubcategory::getCostCategory, costCategory)
                .orderByAsc(BizCostSubcategory::getSortOrder);
        return costSubcategoryMapper.selectList(wrapper);
    }

    /**
     * 新增
     */
    public void save(BizCostSubcategory subcategory) {
        costSubcategoryMapper.insert(subcategory);
    }

    /**
     * 更新
     */
    public void update(BizCostSubcategory subcategory) {
        BizCostSubcategory existing = costSubcategoryMapper.selectById(subcategory.getId());
        if (existing == null) {
            throw new BusinessException("费用子类不存在");
        }
        costSubcategoryMapper.updateById(subcategory);
    }

    /**
     * 删除
     */
    public void delete(Long id) {
        BizCostSubcategory existing = costSubcategoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("费用子类不存在");
        }
        costSubcategoryMapper.deleteById(id);
    }
}
