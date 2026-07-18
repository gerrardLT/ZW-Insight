package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizMaterialInventory;
import com.zwinsight.material.domain.BizMaterialInventoryDetail;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizMaterialInventoryDetailMapper;
import com.zwinsight.material.mapper.BizMaterialInventoryMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 材料盘点服务
 * <p>两阶段：①登记(save)只保存盘点单(DRAFT)与盘点明细，不动库存；
 * ②审批(submit)据实盘数量调整库存并留存盘盈亏差异。</p>
 */
@Service
@RequiredArgsConstructor
public class MaterialInventoryService {

    private final BizMaterialInventoryMapper inventoryMapper;
    private final BizMaterialInventoryDetailMapper detailMapper;
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
     * 登记盘点单（第一阶段）
     * <p>仅保存盘点单(DRAFT)与盘点明细快照（账面/实盘/差异），不调整库存。
     * 库存调整须经审批（{@link #submit(Long)}）后执行。</p>
     * adjustments: key=stockId, value=实盘数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizMaterialInventory inventory, Map<Long, BigDecimal> adjustments) {
        inventory.setStatus("DRAFT");
        inventoryMapper.insert(inventory);

        if (adjustments != null && !adjustments.isEmpty()) {
            for (Map.Entry<Long, BigDecimal> entry : adjustments.entrySet()) {
                BizProjectMaterialStock stock = stockMapper.selectById(entry.getKey());
                if (stock == null) {
                    continue;
                }
                BigDecimal book = stock.getStockQuantity() == null ? BigDecimal.ZERO : stock.getStockQuantity();
                BigDecimal actual = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();

                BizMaterialInventoryDetail detail = new BizMaterialInventoryDetail();
                detail.setInventoryId(inventory.getId());
                detail.setStockId(stock.getId());
                detail.setMaterialName(stock.getMaterialName());
                detail.setSpecification(stock.getSpecification());
                detail.setUnit(stock.getUnit());
                detail.setBookQuantity(book);
                detail.setActualQuantity(actual);
                detail.setDiffQuantity(actual.subtract(book));
                detailMapper.insert(detail);
            }
        }
    }

    /**
     * 审批盘点单（第二阶段）
     * <p>据盘点明细的实盘数量调整库存；差异已在明细中留痕（盘盈亏流水）。
     * 仅 DRAFT 状态可审批，审批后置为 APPROVED。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizMaterialInventory inventory = inventoryMapper.selectById(id);
        if (inventory == null) throw new BusinessException("盘点单不存在");
        if (!"DRAFT".equals(inventory.getStatus())) throw new BusinessException("仅草稿状态可审批");

        LambdaQueryWrapper<BizMaterialInventoryDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizMaterialInventoryDetail::getInventoryId, id);
        List<BizMaterialInventoryDetail> details = detailMapper.selectList(wrapper);

        for (BizMaterialInventoryDetail detail : details) {
            BizProjectMaterialStock stock = stockMapper.selectById(detail.getStockId());
            if (stock == null) {
                continue;
            }
            stock.setStockQuantity(detail.getActualQuantity());
            stockMapper.updateById(stock);
        }

        inventory.setStatus("APPROVED");
        inventoryMapper.updateById(inventory);
    }

    public BizMaterialInventory getById(Long id) {
        BizMaterialInventory inventory = inventoryMapper.selectById(id);
        if (inventory == null) throw new BusinessException("盘点单不存在");
        return inventory;
    }

    public void update(BizMaterialInventory inventory) {
        BizMaterialInventory existing = inventoryMapper.selectById(inventory.getId());
        if (existing == null) throw new BusinessException("盘点单不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        inventoryMapper.updateById(inventory);
    }

    public void delete(Long id) {
        BizMaterialInventory existing = inventoryMapper.selectById(id);
        if (existing == null) throw new BusinessException("盘点单不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        inventoryMapper.deleteById(id);
    }
}
