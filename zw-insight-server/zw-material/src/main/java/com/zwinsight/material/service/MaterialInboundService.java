package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.*;
import com.zwinsight.material.mapper.*;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 材料入库服务
 */
@Service
@RequiredArgsConstructor
public class MaterialInboundService {

    private final BizMaterialInboundMapper inboundMapper;
    private final BizMaterialInboundDetailMapper inboundDetailMapper;
    private final BizProjectMaterialStockMapper stockMapper;
    private final BizMaterialOutboundMapper outboundMapper;
    private final BizMaterialOutboundDetailMapper outboundDetailMapper;
    private final BizPurchaseContractMapper purchaseContractMapper;

    /**
     * 分页查询
     */
    public PageResult<BizMaterialInbound> page(int page, int size, Long projectId) {
        Page<BizMaterialInbound> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMaterialInbound> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizMaterialInbound::getProjectId, projectId)
                .orderByDesc(BizMaterialInbound::getCreatedAt);
        Page<BizMaterialInbound> result = inboundMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BizMaterialInbound getById(Long id) {
        BizMaterialInbound inbound = inboundMapper.selectById(id);
        if (inbound == null) throw new BusinessException("入库单不存在");
        return inbound;
    }

    /**
     * 更新入库单
     */
    public void update(BizMaterialInbound inbound) {
        BizMaterialInbound existing = inboundMapper.selectById(inbound.getId());
        if (existing == null) throw new BusinessException("入库单不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        inboundMapper.updateById(inbound);
    }

    /**
     * 删除入库单
     */
    public void delete(Long id) {
        BizMaterialInbound existing = inboundMapper.selectById(id);
        if (existing == null) throw new BusinessException("入库单不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        inboundMapper.deleteById(id);
    }

    /**
     * 保存入库单（更新库存,更新合同cumulativeInbound; 直接出库则同时生成outbound）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizMaterialInbound inbound, List<BizMaterialInboundDetail> details) {
        inbound.setStatus("DRAFT");
        inboundMapper.insert(inbound);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (BizMaterialInboundDetail detail : details) {
            detail.setInboundId(inbound.getId());
            if (detail.getTotalPrice() == null && detail.getUnitPrice() != null && detail.getQuantity() != null) {
                detail.setTotalPrice(detail.getUnitPrice().multiply(detail.getQuantity()));
            }
            totalAmount = totalAmount.add(detail.getTotalPrice() != null ? detail.getTotalPrice() : BigDecimal.ZERO);
            inboundDetailMapper.insert(detail);
        }

        inbound.setTotalAmount(totalAmount);
        inboundMapper.updateById(inbound);
    }

    /**
     * 提交入库（更新库存、合同累计入库；直接出库则生成出库单）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizMaterialInbound inbound = inboundMapper.selectById(id);
        if (inbound == null) {
            throw new BusinessException("入库单不存在");
        }
        if (!"DRAFT".equals(inbound.getStatus())) {
            throw new BusinessException("仅草稿状态可提交");
        }

        inbound.setStatus("APPROVED");
        inboundMapper.updateById(inbound);

        // 获取明细
        List<BizMaterialInboundDetail> details = inboundDetailMapper.selectList(
                new LambdaQueryWrapper<BizMaterialInboundDetail>().eq(BizMaterialInboundDetail::getInboundId, id));

        // 更新库存
        for (BizMaterialInboundDetail detail : details) {
            updateStock(inbound.getProjectId(), detail, true);
        }

        // 更新采购合同累计入库金额
        if (inbound.getContractId() != null) {
            BizPurchaseContract contract = purchaseContractMapper.selectById(inbound.getContractId());
            if (contract != null) {
                BigDecimal cumulative = contract.getCumulativeInbound() != null ? contract.getCumulativeInbound() : BigDecimal.ZERO;
                contract.setCumulativeInbound(cumulative.add(inbound.getTotalAmount() != null ? inbound.getTotalAmount() : BigDecimal.ZERO));
                purchaseContractMapper.updateById(contract);
            }
        }

        // 直接出库
        if (Integer.valueOf(1).equals(inbound.getDirectOutbound())) {
            BizMaterialOutbound outbound = new BizMaterialOutbound();
            outbound.setProjectId(inbound.getProjectId());
            outbound.setOutboundType("PICK");
            outbound.setOutboundDate(inbound.getInboundDate());
            outbound.setStatus("APPROVED");
            outboundMapper.insert(outbound);

            for (BizMaterialInboundDetail detail : details) {
                BizMaterialOutboundDetail outDetail = new BizMaterialOutboundDetail();
                outDetail.setOutboundId(outbound.getId());
                outDetail.setMaterialName(detail.getMaterialName());
                outDetail.setSpecification(detail.getSpecification());
                outDetail.setUnit(detail.getUnit());
                outDetail.setQuantity(detail.getQuantity());
                outDetail.setUnitPrice(detail.getUnitPrice());
                outboundDetailMapper.insert(outDetail);

                // 出库减少库存
                updateStock(inbound.getProjectId(), detail, false);
            }
        }
    }

    /**
     * 更新库存
     */
    private void updateStock(Long projectId, BizMaterialInboundDetail detail, boolean isInbound) {
        LambdaQueryWrapper<BizProjectMaterialStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMaterialStock::getProjectId, projectId)
                .eq(BizProjectMaterialStock::getMaterialName, detail.getMaterialName())
                .eq(BizProjectMaterialStock::getSpecification, detail.getSpecification());
        BizProjectMaterialStock stock = stockMapper.selectOne(wrapper);

        BigDecimal qty = detail.getQuantity() != null ? detail.getQuantity() : BigDecimal.ZERO;
        BigDecimal price = detail.getUnitPrice() != null ? detail.getUnitPrice() : BigDecimal.ZERO;

        if (stock == null) {
            stock = new BizProjectMaterialStock();
            stock.setProjectId(projectId);
            stock.setMaterialName(detail.getMaterialName());
            stock.setSpecification(detail.getSpecification());
            stock.setUnit(detail.getUnit());
            stock.setStockQuantity(isInbound ? qty : BigDecimal.ZERO);
            stock.setAvgUnitPrice(price);
            stock.setTotalInbound(isInbound ? qty : BigDecimal.ZERO);
            stock.setTotalOutbound(isInbound ? BigDecimal.ZERO : qty);
            stock.setTotalReturn(BigDecimal.ZERO);
            stock.setTotalTransferIn(BigDecimal.ZERO);
            stock.setTotalTransferOut(BigDecimal.ZERO);
            stockMapper.insert(stock);
        } else {
            if (isInbound) {
                // 加权平均单价
                BigDecimal oldTotal = stock.getStockQuantity().multiply(stock.getAvgUnitPrice() != null ? stock.getAvgUnitPrice() : BigDecimal.ZERO);
                BigDecimal newTotal = oldTotal.add(qty.multiply(price));
                BigDecimal newQty = stock.getStockQuantity().add(qty);
                if (newQty.compareTo(BigDecimal.ZERO) > 0) {
                    stock.setAvgUnitPrice(newTotal.divide(newQty, 4, RoundingMode.HALF_UP));
                }
                stock.setStockQuantity(newQty);
                stock.setTotalInbound(stock.getTotalInbound().add(qty));
            } else {
                stock.setStockQuantity(stock.getStockQuantity().subtract(qty));
                stock.setTotalOutbound(stock.getTotalOutbound().add(qty));
            }
            stockMapper.updateById(stock);
        }
    }
}
