package com.zwinsight.purchase.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.common.event.project.ProjectDeletedEvent;
import com.zwinsight.purchase.domain.BizPurchaseContract;
import com.zwinsight.purchase.domain.BizPurchaseSettlement;
import com.zwinsight.purchase.mapper.BizPurchaseContractMapper;
import com.zwinsight.purchase.mapper.BizPurchaseSettlementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目删除级联清理监听器（zw-purchase 模块，R7-02）。
 * <p>监听 {@link ProjectDeletedEvent}，清理本模块含 {@code project_id} 列的 2 张表。
 * 实现约定与异常传播策略详见
 * {@code com.zwinsight.contract.listener.ContractProjectCascadeCleanupListener} 的类注释。</p>
 *
 * <p>采购合同明细（biz_purchase_contract_detail）与询价/报价系列表以
 * {@code contract_id}/{@code inquiry_id} 关联、无 {@code project_id} 列，
 * 不在本监听器职责内；合同逻辑删除后明细随主单失效，查询侧一律经主单 JOIN。</p>
 *
 * <p>线上取证（2026-09-18 Round 7）：本模块遗留孤儿——采购结算 9+3 条、采购合同 2+4 条。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseProjectCascadeCleanupListener {

    private final BizPurchaseContractMapper purchaseContractMapper;
    private final BizPurchaseSettlementMapper purchaseSettlementMapper;

    @EventListener
    public void onProjectDeleted(ProjectDeletedEvent event) {
        Long projectId = event.getProjectId();
        int contracts = purchaseContractMapper.delete(
                new QueryWrapper<BizPurchaseContract>().eq("project_id", projectId));
        int settlements = purchaseSettlementMapper.delete(
                new QueryWrapper<BizPurchaseSettlement>().eq("project_id", projectId));

        log.info("项目删除级联清理[purchase]完成, projectId={}, 采购合同={} 采购结算={}",
                projectId, contracts, settlements);
    }
}
