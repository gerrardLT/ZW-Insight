package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizMaterialInventory;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizMaterialInventoryMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 材料盘点服务
 */
@Service
@RequiredArgsConstructor
public class MaterialInventoryService {

    private final BizMaterialInventoryMapper inventoryMapper;
    private final BizProjectMaterialStockMapper stockMapper;

    /**
     * 分页查询
     */
    public PageResult<BizMaterialInventory> page(int page, int size, Long projectId) {
        Page<BizMaterialInventory> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMaterialInventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizMaterialInventory::getProjectId, projectId)
                .orderByDesc(BizMaterialInventory::getCreatedAt);
        Page<BizMaterialInventory> result = inventoryMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存盘点单（差异调整库存）
     * adjustments: key=stockId, value=实际盘点数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizMaterialInventory inventory, Map<Long, BigDecimal> adjustments) {
        inventory.setStatus("DRAFT");
        inventoryMapper.insert(inventory);

        if (adjustments != null && !adjustments.isEmpty()) {
            for (Map.Entry<Long, BigDecimal> entry : adjustments.entrySet()) {
                BizProjectMaterialStock stock = stockMapper.selectById(entry.getKey());
                if (stock != null) {
                    stock.setStockQuantity(entry.getValue());
                    stockMapper.updateById(stock);
                }
            }
        }
    }
}
