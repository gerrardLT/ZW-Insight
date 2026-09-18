package com.zwinsight.material.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.material.domain.BizMaterialInbound;
import com.zwinsight.material.domain.BizMaterialInventory;
import com.zwinsight.material.domain.BizMaterialOutbound;
import com.zwinsight.material.domain.BizMaterialRefund;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.domain.BizStockWarningConfig;
import com.zwinsight.material.mapper.BizMaterialInboundMapper;
import com.zwinsight.material.mapper.BizMaterialInventoryMapper;
import com.zwinsight.material.mapper.BizMaterialOutboundMapper;
import com.zwinsight.material.mapper.BizMaterialRefundMapper;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.material.mapper.BizStockWarningConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-material 模块，R7-02）。
 * <p>监听 {@link ProjectDeletedEvent}，清理本模块含 {@code project_id} 列的 6 张表。
 * 实现约定与异常传播策略详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释
 * （各模块统一：MyBatis-Plus 逻辑删除 + 字符串列名 + 不吞异常 + 幂等）。</p>
 *
 * <p>线上取证（2026-09-18 Round 7）：本模块遗留孤儿——材料入库 15+17 条、
 * 出库 16+17 条、项目库存 24 条、材料盘点 15 条、退料 4 条；其中 4 条库存记录
 * 因「入库单被删但出库单未删」呈现 stock_quantity=-30 的负值（R7-01）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialProjectCascadeCleanupListener {

    private final BizMaterialInboundMapper materialInboundMapper;
    private final BizMaterialOutboundMapper materialOutboundMapper;
    private final BizProjectMaterialStockMapper projectMaterialStockMapper;
    private final BizMaterialInventoryMapper materialInventoryMapper;
    private final BizMaterialRefundMapper materialRefundMapper;
    private final BizStockWarningConfigMapper stockWarningConfigMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int inbound = materialInboundMapper.delete(
                new QueryWrapper<BizMaterialInbound>().eq("project_id", projectId));
        int outbound = materialOutboundMapper.delete(
                new QueryWrapper<BizMaterialOutbound>().eq("project_id", projectId));
        int stock = projectMaterialStockMapper.delete(
                new QueryWrapper<BizProjectMaterialStock>().eq("project_id", projectId));
        int inventory = materialInventoryMapper.delete(
                new QueryWrapper<BizMaterialInventory>().eq("project_id", projectId));
        int refund = materialRefundMapper.delete(
                new QueryWrapper<BizMaterialRefund>().eq("project_id", projectId));
        int warningConfig = stockWarningConfigMapper.delete(
                new QueryWrapper<BizStockWarningConfig>().eq("project_id", projectId));

        log.info("项目删除级联清理[material]完成, projectId={}, 入库={} 出库={} 库存={} "
                        + "盘点={} 退料={} 预警配置={}",
                projectId, inbound, outbound, stock, inventory, refund, warningConfig);
    }
}
