package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.dashboard.mapper.DrillDownMapper;
import com.zwinsight.finance.mapper.BizInvoiceReceivedMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.material.mapper.BizMaterialInboundMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DrillDownService 单元测试（驾驶舱 P2-4 单据穿透链）。
 * <p>核心断言：类别白名单校验（非法值 400 不静默）、CBS→合同类别路由映射、
 * 口径说明必须随响应下发（basis/note/attachmentNote）、无数据如实为空不伪造。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrillDownServiceTest {

    @Mock private DrillDownMapper drillDownMapper;
    @Mock private BizCostAccountMapper costAccountMapper;
    @Mock private BizPaymentApplyMapper paymentApplyMapper;
    @Mock private BizInvoiceReceivedMapper invoiceReceivedMapper;
    @Mock private BizMaterialInboundMapper materialInboundMapper;

    @InjectMocks
    private DrillDownService drillDownService;

    private BizCostAccount account(Long id, String category, String code, String name,
                                   String baseline, String current, String commitment,
                                   String actual, String forecast) {
        BizCostAccount a = new BizCostAccount();
        a.setId(id);
        a.setProjectId(1L);
        a.setCostCategory(category);
        a.setAccountCode(code);
        a.setAccountName(name);
        a.setBaselineAmount(new BigDecimal(baseline));
        a.setCurrentAmount(new BigDecimal(current));
        a.setCommitmentAmount(new BigDecimal(commitment));
        a.setActualAmount(new BigDecimal(actual));
        a.setForecastAmount(new BigDecimal(forecast));
        return a;
    }

    private Map<String, Object> supplierRow(String name, String contractAmount,
                                            String settlement, String paid) {
        Map<String, Object> row = new HashMap<>();
        row.put("supplierName", name);
        row.put("contractCount", 2L);
        row.put("contractAmount", new BigDecimal(contractAmount));
        row.put("settlementTotal", new BigDecimal(settlement));
        row.put("paidTotal", new BigDecimal(paid));
        row.put("invoiceTotal", new BigDecimal(paid));
        return row;
    }

    @Nested
    @DisplayName("getCostCategories() 项目→成本分类")
    class CostCategoriesTests {

        @Test
        @DisplayName("正常路径 — CBS 六类聚合 + 合同侧对照数 + 口径说明")
        void aggregatesCbsAndContractSide() {
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    account(11L, "MATERIAL", "01.01", "钢筋", "2000000", "2200000",
                            "1800000", "1500000", "2300000"),
                    account(12L, "MATERIAL", "01.02", "混凝土", "1000000", "1000000",
                            "900000", "800000", "1100000"),
                    account(13L, "INDIRECT", "05.01", "招待费", "100000", "100000",
                            "0", "50000", "60000")));
            when(drillDownMapper.supplierAggPurchase(1L)).thenReturn(List.of(
                    supplierRow("甲钢铁", "2500000", "2200000", "2000000")));
            when(drillDownMapper.supplierAggLabor(1L)).thenReturn(List.of());
            when(drillDownMapper.supplierAggMachine(1L)).thenReturn(List.of());
            when(drillDownMapper.supplierAggSubcontract(1L)).thenReturn(List.of());
            when(drillDownMapper.supplierAggOther(1L)).thenReturn(List.of());

            Map<String, Object> result = drillDownService.getCostCategories(1L);

            assertThat(result.get("hasCbs")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            // 无账户的类别不占行：只剩 MATERIAL 与 INDIRECT
            assertThat(rows).hasSize(2);
            Map<String, Object> material = rows.get(0);
            assertThat(material.get("costCategory")).isEqualTo("MATERIAL");
            // CBS→合同类别路由：MATERIAL 穿透到采购合同表（PURCHASE 词汇表）
            assertThat(material.get("contractCategory")).isEqualTo("PURCHASE");
            assertThat((BigDecimal) material.get("baseline"))
                    .isEqualByComparingTo("3000000");
            assertThat((BigDecimal) material.get("actual"))
                    .isEqualByComparingTo("2300000");
            assertThat((BigDecimal) material.get("contractSettlement"))
                    .isEqualByComparingTo("2200000");
            assertThat(String.valueOf(material.get("basis"))).contains("出库");
            Map<String, Object> indirect = rows.get(1);
            assertThat(indirect.get("contractCategory")).isEqualTo("OTHER_EXPENSE");
            // 间接费口径如实声明：无供应商维度
            assertThat(String.valueOf(indirect.get("basis"))).contains("报销");
        }

        @Test
        @DisplayName("无 CBS 账户 — 如实返回空行并给出口径提示（不伪造类别行）")
        void noCbsReturnsHonestEmpty() {
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(drillDownMapper.supplierAggPurchase(anyLong())).thenReturn(List.of());
            when(drillDownMapper.supplierAggLabor(anyLong())).thenReturn(List.of());
            when(drillDownMapper.supplierAggMachine(anyLong())).thenReturn(List.of());
            when(drillDownMapper.supplierAggSubcontract(anyLong())).thenReturn(List.of());
            when(drillDownMapper.supplierAggOther(anyLong())).thenReturn(List.of());

            Map<String, Object> result = drillDownService.getCostCategories(1L);

            assertThat(result.get("hasCbs")).isEqualTo(false);
            assertThat((List<?>) result.get("rows")).isEmpty();
            assertThat(String.valueOf(result.get("note"))).contains("未建 CBS");
            // 附件环节现状说明必须下发（前端如实展示，不伪造跳转）
            assertThat(String.valueOf(result.get("attachmentNote"))).contains("未支持");
        }

        @Test
        @DisplayName("projectId 缺失 — 400 拒绝")
        void nullProjectIdRejected() {
            assertThatThrownBy(() -> drillDownService.getCostCategories(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("项目ID");
        }
    }

    @Nested
    @DisplayName("getSuppliers() 成本分类→供应商")
    class SuppliersTests {

        @Test
        @DisplayName("正常路径 — 按结算降序 + 类别名下发")
        void sortedBySettlement() {
            when(drillDownMapper.supplierAggPurchase(1L)).thenReturn(List.of(
                    supplierRow("小供应商", "500000", "100000", "50000"),
                    supplierRow("大供应商", "3000000", "2200000", "2000000")));

            Map<String, Object> result = drillDownService.getSuppliers(1L, "PURCHASE");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            assertThat(rows.get(0).get("supplierName")).isEqualTo("大供应商");
            assertThat(result.get("categoryName")).asString().contains("材料");
        }

        @Test
        @DisplayName("OTHER_EXPENSE — 附加强提示：报销无供应商维度")
        void otherExpenseCarriesNote() {
            when(drillDownMapper.supplierAggOther(1L)).thenReturn(List.of());

            Map<String, Object> result = drillDownService.getSuppliers(1L, "OTHER_EXPENSE");

            assertThat(String.valueOf(result.get("note"))).contains("成本流水");
        }

        @Test
        @DisplayName("非法类别 — 400 且提示可选值（不静默当作全部）")
        void invalidCategoryRejected() {
            assertThatThrownBy(() -> drillDownService.getSuppliers(1L, "MATERIAL"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("OTHER_EXPENSE");
            assertThatThrownBy(() -> drillDownService.getSuppliers(1L, null))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("getCategoryTxn() 成本分类→实际成本流水")
    class CategoryTxnTests {

        @Test
        @DisplayName("PURCHASE 映射 CBS MATERIAL 单类查询")
        void purchaseMapsToMaterialCategory() {
            when(drillDownMapper.accountActualTxn(eq(1L), eq(List.of("MATERIAL"))))
                    .thenReturn(List.of());

            Map<String, Object> result = drillDownService.getCategoryTxn(1L, "PURCHASE");

            verify(drillDownMapper).accountActualTxn(1L, List.of("MATERIAL"));
            assertThat(String.valueOf(result.get("basis"))).contains("ROLLUP");
        }

        @Test
        @DisplayName("OTHER_EXPENSE 覆盖 INDIRECT+OTHER 两类账户")
        void otherExpenseCoversIndirectAndOther() {
            when(drillDownMapper.accountActualTxn(eq(1L), anyList())).thenReturn(List.of());

            drillDownService.getCategoryTxn(1L, "OTHER_EXPENSE");

            verify(drillDownMapper).accountActualTxn(1L, List.of("INDIRECT", "OTHER"));
        }
    }

    @Nested
    @DisplayName("getContracts() 供应商→合同")
    class ContractsTests {

        @Test
        @DisplayName("supplierName 空白视为不限（查该类全部，不透传空串精确匹配）")
        void blankSupplierTreatedAsAll() {
            when(drillDownMapper.contractsLabor(1L, null)).thenReturn(List.of());

            Map<String, Object> result = drillDownService.getContracts(1L, "LABOR", "  ");

            verify(drillDownMapper).contractsLabor(1L, null);
            assertThat(result.get("supplierName")).isNull();
        }

        @Test
        @DisplayName("机械/分包/其他合同 — 声明合同级无审批流程列")
        void noWorkflowColumnNote() {
            when(drillDownMapper.contractsMachine(eq(1L), isNull())).thenReturn(List.of());

            Map<String, Object> result = drillDownService.getContracts(1L, "MACHINE", null);

            assertThat(String.valueOf(result.get("note"))).contains("审批");
        }
    }

    @Nested
    @DisplayName("getContractDocs() 合同→原始单据")
    class ContractDocsTests {

        @Test
        @DisplayName("PURCHASE — 结算+付款+收票+入库四类单据聚合")
        void purchaseAggregatesFourDocTypes() {
            Map<String, Object> settlement = new HashMap<>();
            settlement.put("id", 901L);
            settlement.put("docNo", "CGJS-001");
            settlement.put("amount", new BigDecimal("100000"));
            settlement.put("status", "APPROVED");
            settlement.put("refInboundCode", "RK-001");
            when(drillDownMapper.settlementsPurchase(500L)).thenReturn(List.of(settlement));
            when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(invoiceReceivedMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(materialInboundMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            Map<String, Object> result = drillDownService.getContractDocs("PURCHASE", 500L);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("docType")).isEqualTo("SETTLEMENT");
            assertThat(rows.get(0).get("docNo")).isEqualTo("CGJS-001");
            // 采购结算行带出入库单关联（§13 采购→入库环节）
            assertThat(rows.get(0).get("refInboundCode")).isEqualTo("RK-001");
            verify(materialInboundMapper).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("OTHER_EXPENSE — 无结算单表（如实跳过并提示），不查结算 Mapper")
        void otherExpenseHasNoSettlementTable() {
            when(paymentApplyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(invoiceReceivedMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            Map<String, Object> result = drillDownService.getContractDocs("OTHER_EXPENSE", 600L);

            verify(drillDownMapper, never()).settlementsPurchase(anyLong());
            assertThat(String.valueOf(result.get("note"))).contains("无结算单表");
        }

        @Test
        @DisplayName("非法类别 / 缺合同ID — 400 拒绝")
        void invalidParamsRejected() {
            assertThatThrownBy(() -> drillDownService.getContractDocs("BOGUS", 1L))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> drillDownService.getContractDocs("LABOR", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("合同ID");
        }
    }
}
