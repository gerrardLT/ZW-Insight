package com.zwinsight.contract.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizBomItem;
import com.zwinsight.contract.dto.BomItemExcelDTO;
import com.zwinsight.contract.mapper.BizBomItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工程量清单服务
 */
@Service
@RequiredArgsConstructor
public class BomItemService {

    private final BizBomItemMapper bomItemMapper;

    /**
     * 按合同查询清单列表
     */
    public List<BizBomItem> list(Long contractId) {
        LambdaQueryWrapper<BizBomItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBomItem::getContractId, contractId)
                .orderByAsc(BizBomItem::getSortOrder);
        return bomItemMapper.selectList(wrapper);
    }

    /**
     * 新增清单项
     */
    public void save(BizBomItem item) {
        // 计算金额 = 数量 × 单价
        if (item.getQuantity() != null && item.getUnitPrice() != null) {
            item.setAmount(item.getQuantity().multiply(item.getUnitPrice()));
        }
        bomItemMapper.insert(item);
    }

    /**
     * 更新清单项
     */
    public void update(BizBomItem item) {
        BizBomItem existing = bomItemMapper.selectById(item.getId());
        if (existing == null) {
            throw new BusinessException("清单项不存在");
        }
        // 重新计算金额
        if (item.getQuantity() != null && item.getUnitPrice() != null) {
            item.setAmount(item.getQuantity().multiply(item.getUnitPrice()));
        }
        bomItemMapper.updateById(item);
    }

    /**
     * 删除清单项
     */
    public void delete(Long id) {
        BizBomItem existing = bomItemMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("清单项不存在");
        }
        bomItemMapper.deleteById(id);
    }

    /**
     * 批量导入（EasyExcel）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchImport(Long projectId, Long contractId, MultipartFile file) {
        try {
            AtomicInteger sortOrder = new AtomicInteger(1);
            List<BomItemExcelDTO> dataList = EasyExcel.read(file.getInputStream())
                    .head(BomItemExcelDTO.class)
                    .sheet()
                    .doReadSync();

            for (BomItemExcelDTO dto : dataList) {
                BizBomItem item = new BizBomItem();
                item.setProjectId(projectId);
                item.setContractId(contractId);
                item.setItemName(dto.getItemName());
                item.setSpecification(dto.getSpecification());
                item.setUnit(dto.getUnit());
                item.setQuantity(dto.getQuantity());
                item.setUnitPrice(dto.getUnitPrice());
                // 计算金额
                if (dto.getQuantity() != null && dto.getUnitPrice() != null) {
                    item.setAmount(dto.getQuantity().multiply(dto.getUnitPrice()));
                } else {
                    item.setAmount(BigDecimal.ZERO);
                }
                item.setSortOrder(sortOrder.getAndIncrement());
                bomItemMapper.insert(item);
            }
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }
    }
}
