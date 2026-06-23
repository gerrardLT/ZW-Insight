package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizMaterialOutbound;
import com.zwinsight.material.domain.BizMaterialOutboundDetail;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizMaterialOutboundDetailMapper;
import com.zwinsight.material.mapper.BizMaterialOutboundMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 材料出库服务
 */
@Service
@RequiredArgsConstructor
public class MaterialOutboundService {

    private final BizMaterialOutboundMapper outboundMapper;
    private final BizMaterialOutboundDetailMapper outboundDetailMapper;
    private final BizProjectMaterialStockMapper stockMapper;

    /**
     * 分页查询
     */
    public PageResult<BizMaterialOutbound> page(int page, int size, Long projectId, String outboundType) {
        Page<BizMaterialOutbound> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMaterialOutbound> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizMaterialOutbound::getProjectId, projectId)
                .eq(outboundType != null, BizMaterialOutbound::getOutboundType, outboundType)
                .orderByDesc(BizMaterialOutbound::getCreatedAt);
        Page<BizMaterialOutbound> result = outboundMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 保存出库单（领料:校验库存充足→库存减少; 退货:库存减少）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizMaterialOutbound outbound, List<BizMaterialOutboundDetail> details) {
        outbound.setStatus("DRAFT");
        outboundMapper.insert(outbound);

        for (BizMaterialOutboundDetail detail : details) {
            detail.setOutboundId(outbound.getId());
            outboundDetailMapper.insert(detail);

            // 校验库存
            LambdaQueryWrapper<BizProjectMaterialStock> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizProjectMaterialStock::getProjectId, outbound.getProjectId())
                    .eq(BizProjectMaterialStock::getMaterialName, detail.getMaterialName())
                    .eq(BizProjectMaterialStock::getSpecification, detail.getSpecification());
            BizProjectMaterialStock stock = stockMapper.selectOne(wrapper);

            BigDecimal qty = detail.getQuantity() != null ? detail.getQuantity() : BigDecimal.ZERO;

            if ("PICK".equals(outbound.getOutboundType())) {
                if (stock == null || stock.getStockQuantity().compareTo(qty) < 0) {
                    throw new BusinessException("材料[" + detail.getMaterialName() + "]库存不足");
                }
                stock.setStockQuantity(stock.getStockQuantity().subtract(qty));
                stock.setTotalOutbound(stock.getTotalOutbound().add(qty));
            } else {
                // 退货
                if (stock == null || stock.getStockQuantity().compareTo(qty) < 0) {
                    throw new BusinessException("材料[" + detail.getMaterialName() + "]库存不足，无法退货");
                }
                stock.setStockQuantity(stock.getStockQuantity().subtract(qty));
                stock.setTotalReturn(stock.getTotalReturn().add(qty));
            }
            stockMapper.updateById(stock);
        }
    }
}
