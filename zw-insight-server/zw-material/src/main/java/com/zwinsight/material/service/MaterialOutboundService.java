package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizMaterialOutbound;
import com.zwinsight.material.domain.BizMaterialOutboundDetail;
import com.zwinsight.material.domain.BizMaterialRefund;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.event.MaterialReturnCreatedEvent;
import com.zwinsight.material.mapper.BizMaterialOutboundDetailMapper;
import com.zwinsight.material.mapper.BizMaterialOutboundMapper;
import com.zwinsight.material.mapper.BizMaterialRefundMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 材料出库服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialOutboundService {

    private final BizMaterialOutboundMapper outboundMapper;
    private final BizMaterialOutboundDetailMapper outboundDetailMapper;
    private final BizProjectMaterialStockMapper stockMapper;
    private final BizMaterialRefundMapper refundMapper;
    private final BizProjectMapper projectMapper;
    private final ApplicationEventPublisher eventPublisher;

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
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizMaterialOutbound::getProjectId, BizMaterialOutbound::setProjectName);
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

            // P2 修复（2026-08-12，批次二 MAT-16/D4）：数量必须大于 0，
            // 负数量经 subtract 反向加库存且污染出入库统计
            if (qty.signum() <= 0) {
                throw new BusinessException("材料[" + detail.getMaterialName() + "]出库数量必须大于0");
            }

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

        // 退货出库且关联了采购合同时，发布退货事件以触发退款申请生成
        if ("RETURN".equals(outbound.getOutboundType()) && outbound.getContractId() != null) {
            publishReturnEvent(outbound, details);
        }
    }

    /**
     * 发布退货出库事件
     */
    private void publishReturnEvent(BizMaterialOutbound outbound, List<BizMaterialOutboundDetail> details) {
        List<MaterialReturnCreatedEvent.OutboundDetailItem> eventDetails = new ArrayList<>();
        for (BizMaterialOutboundDetail detail : details) {
            // unitPrice 作为入库单价使用（退货时按入库价计算退款）
            BigDecimal inboundUnitPrice = detail.getUnitPrice() != null
                    ? detail.getUnitPrice() : BigDecimal.ZERO;
            // P2 修复（D8）：quantity null 归一为 0，防下游 multiply 抛 NPE 连带出库事务回滚
            BigDecimal qty = detail.getQuantity() != null ? detail.getQuantity() : BigDecimal.ZERO;
            eventDetails.add(new MaterialReturnCreatedEvent.OutboundDetailItem(
                    detail.getMaterialName(),
                    detail.getSpecification(),
                    detail.getUnit(),
                    qty,
                    inboundUnitPrice
            ));
        }

        MaterialReturnCreatedEvent event = new MaterialReturnCreatedEvent(
                this,
                outbound.getId(),
                outbound.getContractId(),
                outbound.getProjectId(),
                eventDetails
        );
        eventPublisher.publishEvent(event);
    }

    public BizMaterialOutbound getById(Long id) {
        BizMaterialOutbound outbound = outboundMapper.selectById(id);
        if (outbound == null) throw new BusinessException("出库单不存在");
        LambdaQueryWrapper<BizMaterialOutboundDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizMaterialOutboundDetail::getOutboundId, id);
        outbound.setDetails(outboundDetailMapper.selectList(detailWrapper));
        return outbound;
    }

    public void update(BizMaterialOutbound outbound) {
        BizMaterialOutbound existing = outboundMapper.selectById(outbound.getId());
        if (existing == null) throw new BusinessException("出库单不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可编辑");
        outboundMapper.updateById(outbound);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizMaterialOutbound existing = outboundMapper.selectById(id);
        if (existing == null) throw new BusinessException("出库单不存在");
        if (!"DRAFT".equals(existing.getStatus())) throw new BusinessException("仅草稿状态可删除");

        // B3 修复（2026-08-11）：save 时已扣减库存，删除必须对称回填，
        // 否则删除草稿出库单即永久丢失库存
        LambdaQueryWrapper<BizMaterialOutboundDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(BizMaterialOutboundDetail::getOutboundId, id);
        List<BizMaterialOutboundDetail> details = outboundDetailMapper.selectList(detailWrapper);
        for (BizMaterialOutboundDetail detail : details) {
            LambdaQueryWrapper<BizProjectMaterialStock> stockWrapper = new LambdaQueryWrapper<>();
            stockWrapper.eq(BizProjectMaterialStock::getProjectId, existing.getProjectId())
                    .eq(BizProjectMaterialStock::getMaterialName, detail.getMaterialName())
                    .eq(BizProjectMaterialStock::getSpecification, detail.getSpecification());
            BizProjectMaterialStock stock = stockMapper.selectOne(stockWrapper);
            if (stock == null) {
                log.warn("删除出库单时未找到库存记录，跳过回填: outboundId={}, material={}",
                        id, detail.getMaterialName());
                continue;
            }
            BigDecimal qty = detail.getQuantity() != null ? detail.getQuantity() : BigDecimal.ZERO;
            stock.setStockQuantity(stock.getStockQuantity().add(qty));
            if ("PICK".equals(existing.getOutboundType())) {
                stock.setTotalOutbound(stock.getTotalOutbound().subtract(qty));
            } else {
                stock.setTotalReturn(stock.getTotalReturn().subtract(qty));
            }
            stockMapper.updateById(stock);
            outboundDetailMapper.deleteById(detail.getId());
        }

        // P1 修复（2026-08-12，批次二取证枚举）：退货出库 save 时已自动生成并启动
        // PENDING 退款申请，删除出库单必须同步作废退款，否则退款仍可被审批通过
        // 并扣减合同累计已付款（单删钱退的联动断裂）
        if ("RETURN".equals(existing.getOutboundType())) {
            LambdaQueryWrapper<BizMaterialRefund> refundWrapper = new LambdaQueryWrapper<>();
            refundWrapper.eq(BizMaterialRefund::getOutboundId, id)
                    .eq(BizMaterialRefund::getStatus, "PENDING");
            List<BizMaterialRefund> refunds = refundMapper.selectList(refundWrapper);
            for (BizMaterialRefund refund : refunds) {
                refund.setStatus("CANCELED");
                refundMapper.updateById(refund);
                log.info("删除退货出库单同步作废退款申请: outboundId={}, refundId={}", id, refund.getId());
            }
        }

        outboundMapper.deleteById(id);
    }

    public void submit(Long id) {
        BizMaterialOutbound outbound = outboundMapper.selectById(id);
        if (outbound == null) throw new BusinessException("出库单不存在");
        if (!"DRAFT".equals(outbound.getStatus())) throw new BusinessException("仅草稿状态可提交");
        outbound.setStatus("APPROVED");
        outboundMapper.updateById(outbound);
    }
}
