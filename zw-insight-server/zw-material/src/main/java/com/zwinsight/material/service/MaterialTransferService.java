package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizMaterialTransfer;
import com.zwinsight.material.domain.BizMaterialTransferDetail;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizMaterialTransferDetailMapper;
import com.zwinsight.material.mapper.BizMaterialTransferMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 材料调拨服务
 */
@Service
@RequiredArgsConstructor
public class MaterialTransferService {

    private final BizMaterialTransferMapper transferMapper;
    private final BizMaterialTransferDetailMapper transferDetailMapper;
    private final BizProjectMaterialStockMapper stockMapper;
    private final BizProjectMapper projectMapper;

    /**
     * 分页查询
     */
    public PageResult<BizMaterialTransfer> page(int page, int size, Long fromProjectId, Long toProjectId) {
        Page<BizMaterialTransfer> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizMaterialTransfer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(fromProjectId != null, BizMaterialTransfer::getFromProjectId, fromProjectId)
                .eq(toProjectId != null, BizMaterialTransfer::getToProjectId, toProjectId)
                .orderByDesc(BizMaterialTransfer::getCreatedAt);
        Page<BizMaterialTransfer> result = transferMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizMaterialTransfer::getFromProjectId, BizMaterialTransfer::setFromProjectName);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizMaterialTransfer::getToProjectId, BizMaterialTransfer::setToProjectName);
        return PageResult.of(result);
    }

    /**
     * 保存调拨（调出方库存减，调入方库存增）
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizMaterialTransfer transfer, List<BizMaterialTransferDetail> details) {
        transfer.setStatus("DRAFT");
        transferMapper.insert(transfer);

        for (BizMaterialTransferDetail detail : details) {
            detail.setTransferId(transfer.getId());
            transferDetailMapper.insert(detail);

            BigDecimal qty = detail.getQuantity() != null ? detail.getQuantity() : BigDecimal.ZERO;
            BigDecimal price = detail.getUnitPrice() != null ? detail.getUnitPrice() : BigDecimal.ZERO;

            // 调出方库存减少
            LambdaQueryWrapper<BizProjectMaterialStock> fromWrapper = new LambdaQueryWrapper<>();
            fromWrapper.eq(BizProjectMaterialStock::getProjectId, transfer.getFromProjectId())
                    .eq(BizProjectMaterialStock::getMaterialName, detail.getMaterialName())
                    .eq(BizProjectMaterialStock::getSpecification, detail.getSpecification());
            BizProjectMaterialStock fromStock = stockMapper.selectOne(fromWrapper);
            if (fromStock == null || fromStock.getStockQuantity().compareTo(qty) < 0) {
                throw new BusinessException("调出项目材料[" + detail.getMaterialName() + "]库存不足");
            }
            fromStock.setStockQuantity(fromStock.getStockQuantity().subtract(qty));
            fromStock.setTotalTransferOut(fromStock.getTotalTransferOut().add(qty));
            stockMapper.updateById(fromStock);

            // 调入方库存增加
            LambdaQueryWrapper<BizProjectMaterialStock> toWrapper = new LambdaQueryWrapper<>();
            toWrapper.eq(BizProjectMaterialStock::getProjectId, transfer.getToProjectId())
                    .eq(BizProjectMaterialStock::getMaterialName, detail.getMaterialName())
                    .eq(BizProjectMaterialStock::getSpecification, detail.getSpecification());
            BizProjectMaterialStock toStock = stockMapper.selectOne(toWrapper);
            if (toStock == null) {
                toStock = new BizProjectMaterialStock();
                toStock.setProjectId(transfer.getToProjectId());
                toStock.setMaterialName(detail.getMaterialName());
                toStock.setSpecification(detail.getSpecification());
                toStock.setUnit(detail.getUnit());
                toStock.setStockQuantity(qty);
                toStock.setAvgUnitPrice(price);
                toStock.setTotalInbound(BigDecimal.ZERO);
                toStock.setTotalOutbound(BigDecimal.ZERO);
                toStock.setTotalReturn(BigDecimal.ZERO);
                toStock.setTotalTransferIn(qty);
                toStock.setTotalTransferOut(BigDecimal.ZERO);
                stockMapper.insert(toStock);
            } else {
                toStock.setStockQuantity(toStock.getStockQuantity().add(qty));
                toStock.setTotalTransferIn(toStock.getTotalTransferIn().add(qty));
                stockMapper.updateById(toStock);
            }
        }
    }

    public BizMaterialTransfer getById(Long id) {
        BizMaterialTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) throw new BusinessException("调拨单不存在");
        LambdaQueryWrapper<BizMaterialTransferDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizMaterialTransferDetail::getTransferId, id);
        transfer.setDetails(transferDetailMapper.selectList(detailWrapper));
        return transfer;
    }

    public void update(BizMaterialTransfer transfer) {
        BizMaterialTransfer existing = transferMapper.selectById(transfer.getId());
        if (existing == null) throw new BusinessException("调拨单不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        transferMapper.updateById(transfer);
    }

    public void delete(Long id) {
        BizMaterialTransfer existing = transferMapper.selectById(id);
        if (existing == null) throw new BusinessException("调拨单不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");
        transferMapper.deleteById(id);
    }

    public void submit(Long id) {
        BizMaterialTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) throw new BusinessException("调拨单不存在");
        if (!"DRAFT".equals(transfer.getStatus())) throw new BusinessException("仅草稿状态可提交");
        transfer.setStatus("APPROVED");
        transferMapper.updateById(transfer);
    }
}
