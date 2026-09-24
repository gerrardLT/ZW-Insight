package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.dashboard.mapper.DrillDownMapper;
import com.zwinsight.finance.domain.BizInvoiceReceived;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.mapper.BizInvoiceReceivedMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.material.domain.BizMaterialInbound;
import com.zwinsight.material.mapper.BizMaterialInboundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单据穿透链服务（驾驶舱 P2-4，UI §13「任何经营数字最终都必须可追溯」/§16 交互逻辑）。
 *
 * <h3>链路（以真实数据源逐环实证后确定，2026-09-24）</h3>
 * <pre>
 * 经营数字（项目构成，前端复用 project-health）
 *   → 成本分类（biz_cost_account 按 CBS cost_category 聚合）
 *   → 供应商（五类支出合同表按乙方聚合）        ← INDIRECT/OTHER 无供应商维度时走成本流水
 *   → 合同（供应商名下该类合同清单）
 *   → 原始单据（结算单 / 付款申请 / 收票登记 / 入库单[采购]）
 *   → 审批轨迹（wf_approval_record，经 zw-workflow /approval/trace 端点）
 *   → 原始附件（⚠ 无数据源，见 {@link #ATTACHMENT_NOTE}）
 * </pre>
 *
 * <h3>类别词汇表（与 biz_payment_apply.contract_category 同源，实证于付款回写路由）</h3>
 * <p>{@code PURCHASE / LABOR / MACHINE / SUBCONTRACT / OTHER_EXPENSE} 五档。
 * CBS 的 MATERIAL 对应合同侧 PURCHASE（采购合同）；INDIRECT/OTHER 对应 OTHER_EXPENSE。
 * 非法类别直接报 400，<b>不静默当作“全部”</b>。</p>
 *
 * <h3>口径纪律</h3>
 * <ul>
 *   <li>CBS 实际成本 = 已审批结算 + 材料出库（CostRollUpService 归集口径），
 *       与合同侧「累计结算」存在<b>合法差额</b>（出库未结算部分），两侧数值并列呈现不强行轧平；</li>
 *   <li>INDIRECT/OTHER 类的实际成本主要来自报销单据与人工/种子记账（归集任务不覆盖这两类），
 *       供应商维度不成立 → 如实标注并引导走成本流水穿透，<b>不伪造供应商归属</b>；</li>
 *   <li>劳务/机械/分包结算单<b>无单号与业务日期列</b>（实证），登记时间如实取 created_at
 *       并命名「创建时间」，不冒充业务发生日。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DrillDownService {

    /** 穿透链类别词汇表（= 付款申请 contract_category 的模块路由值域） */
    public static final Set<String> DRILL_CATEGORIES =
            Set.of("PURCHASE", "LABOR", "MACHINE", "SUBCONTRACT", "OTHER_EXPENSE");

    /**
     * 原始附件环节的现状说明（实证 2026-09-24）：
     * 付款申请/结算/收票/合同五类支出单据均<b>无附件上传功能</b>
     * （全仓业务实体仅现场整改单有 attachmentIds；file_info 通用表存在但无业务方挂接单据）。
     * 穿透链终点如实止步于审批轨迹，此文案随响应下发，前端展示，<b>不伪造附件环节</b>。
     */
    public static final String ATTACHMENT_NOTE =
            "原始附件环节当前系统未支持：付款/结算/收票/合同单据均无附件上传功能，不伪造跳转";

    /** 合同类别 → 中文 */
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "PURCHASE", "材料（采购合同）",
            "LABOR", "人工（劳务合同）",
            "MACHINE", "机械（机械合同）",
            "SUBCONTRACT", "分包（分包合同）",
            "OTHER_EXPENSE", "其他支出（其他合同）");

    /** CBS 费用类别 → 合同类别（归集路由同源：CostRollUpService 四类目 + 其他兜底） */
    private static final Map<String, String> CBS_TO_CONTRACT_CATEGORY = Map.of(
            "MATERIAL", "PURCHASE",
            "LABOR", "LABOR",
            "MACHINE", "MACHINE",
            "SUBCONTRACT", "SUBCONTRACT",
            "INDIRECT", "OTHER_EXPENSE",
            "OTHER", "OTHER_EXPENSE");

    /** 合同类别 → CBS 费用类别集合（成本流水穿透的反向映射） */
    private static final Map<String, List<String>> CONTRACT_TO_CBS = Map.of(
            "PURCHASE", List.of("MATERIAL"),
            "LABOR", List.of("LABOR"),
            "MACHINE", List.of("MACHINE"),
            "SUBCONTRACT", List.of("SUBCONTRACT"),
            "OTHER_EXPENSE", List.of("INDIRECT", "OTHER"));

    private static final List<String> CBS_ORDER =
            List.of("MATERIAL", "LABOR", "MACHINE", "SUBCONTRACT", "INDIRECT", "OTHER");

    private static final Map<String, String> CBS_NAMES = Map.of(
            "MATERIAL", "材料费", "LABOR", "人工费", "MACHINE", "机械费",
            "SUBCONTRACT", "分包费", "INDIRECT", "间接费", "OTHER", "其他费用");

    private final DrillDownMapper drillDownMapper;
    private final BizCostAccountMapper costAccountMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;
    private final BizInvoiceReceivedMapper invoiceReceivedMapper;
    private final BizMaterialInboundMapper materialInboundMapper;

    // ==================== L2：项目 → 成本分类 ====================

    /**
     * 项目成本分类构成（CBS 六类）+ 合同侧对照数（结算/付款）。
     * <p>CBS 侧与合同侧并列呈现：前者是成本主线口径（结算+出库归集），后者是合同执行口径，
     * 两者差额对 MATERIAL 属合法（出库未结算），对其余类应趋于一致——不一致即数据问题，
     * 由调用方展示两列供老板对账，服务端不静默择一。</p>
     */
    public Map<String, Object> getCostCategories(Long projectId) {
        requireNonNull(projectId, "项目ID不能为空");
        List<BizCostAccount> accounts = costAccountMapper.selectList(
                new LambdaQueryWrapper<BizCostAccount>()
                        .eq(BizCostAccount::getProjectId, projectId)
                        .orderByAsc(BizCostAccount::getAccountCode));

        // CBS 侧：按 cost_category 聚合六维金额
        Map<String, List<BizCostAccount>> byCategory = new LinkedHashMap<>();
        for (BizCostAccount a : accounts) {
            String cat = a.getCostCategory() != null ? a.getCostCategory() : "OTHER";
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(a);
        }
        // 合同侧：一次取各类别供应商聚合后汇总（复用 L3 查询，避免两套 SQL 口径漂移）
        Map<String, BigDecimal[]> contractSide = new HashMap<>();
        for (String category : DRILL_CATEGORIES) {
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal settlement = BigDecimal.ZERO;
            BigDecimal paid = BigDecimal.ZERO;
            for (Map<String, Object> row : querySuppliers(projectId, category)) {
                amount = amount.add(toDecimal(row.get("contractAmount")));
                settlement = settlement.add(toDecimal(row.get("settlementTotal")));
                paid = paid.add(toDecimal(row.get("paidTotal")));
            }
            contractSide.put(category, new BigDecimal[]{amount, settlement, paid});
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String cat : CBS_ORDER) {
            List<BizCostAccount> list = byCategory.getOrDefault(cat, List.of());
            if (list.isEmpty()) {
                continue; // 该类无账户不占行（与成本中心同规则：零行会掩盖“未建 CBS”的事实）
            }
            String token = CBS_TO_CONTRACT_CATEGORY.get(cat);
            BigDecimal[] side = contractSide.getOrDefault(token,
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("costCategory", cat);
            row.put("categoryName", CBS_NAMES.getOrDefault(cat, cat));
            row.put("contractCategory", token);
            row.put("accountCount", list.size());
            row.put("baseline", sum(list, BizCostAccount::getBaselineAmount));
            row.put("current", sum(list, BizCostAccount::getCurrentAmount));
            row.put("commitment", sum(list, BizCostAccount::getCommitmentAmount));
            row.put("actual", sum(list, BizCostAccount::getActualAmount));
            row.put("forecast", sum(list, BizCostAccount::getForecastAmount));
            row.put("contractAmount", side[0]);
            row.put("contractSettlement", side[1]);
            row.put("contractPaid", side[2]);
            row.put("basis", categoryBasis(cat));
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("rows", rows);
        result.put("hasCbs", !accounts.isEmpty());
        result.put("attachmentNote", ATTACHMENT_NOTE);
        if (accounts.isEmpty()) {
            result.put("note", "该项目未建 CBS 成本账户，成本分类无从构成（如实为空，不伪造类别行）");
        }
        return result;
    }

    /** 各类别口径说明（前端必须展示，不得只给数字不给口径） */
    private String categoryBasis(String costCategory) {
        return switch (costCategory) {
            case "MATERIAL" -> "CBS 实际=结算+材料出库归集（CostRollUpTask），与合同侧结算的差额为「已出库未结算」，属合法口径差";
            case "LABOR", "MACHINE", "SUBCONTRACT" -> "实际成本由已审批结算单归集，应与合同侧累计结算一致";
            default -> "间接费/其他的实际成本主要来自报销单据与人工记账（归集任务不覆盖），供应商合同维度不完整，构成请看成本流水";
        };
    }

    // ==================== L3：成本分类 → 供应商 ====================

    /**
     * 类别下的供应商构成（按五类支出合同表聚合）。
     * <p>OTHER_EXPENSE 仅覆盖其他支出合同；间接费主体是报销（无供应商维度），
     * 响应中如实提示，同时给出成本流水入口数据（{@link #getCategoryTxn}）。</p>
     */
    public Map<String, Object> getSuppliers(Long projectId, String contractCategory) {
        requireNonNull(projectId, "项目ID不能为空");
        validateCategory(contractCategory);
        List<Map<String, Object>> rows = new ArrayList<>(querySuppliers(projectId, contractCategory));
        rows.sort(Comparator
                .comparing((Map<String, Object> r) -> toDecimal(r.get("settlementTotal"))).reversed());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("contractCategory", contractCategory);
        result.put("categoryName", CATEGORY_NAMES.get(contractCategory));
        result.put("rows", rows);
        result.put("attachmentNote", ATTACHMENT_NOTE);
        if ("OTHER_EXPENSE".equals(contractCategory)) {
            result.put("note", "仅统计其他支出合同的乙方；间接费的报销/人工记账无供应商维度，请切「成本流水」查看构成");
        }
        return result;
    }

    /** 类别 → 成本账户实际流水（「这个数字怎么来的」的账户级答案） */
    public Map<String, Object> getCategoryTxn(Long projectId, String contractCategory) {
        requireNonNull(projectId, "项目ID不能为空");
        validateCategory(contractCategory);
        List<String> cbsCategories = CONTRACT_TO_CBS.get(contractCategory);
        List<Map<String, Object>> rows =
                drillDownMapper.accountActualTxn(projectId, cbsCategories);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("contractCategory", contractCategory);
        result.put("categoryName", CATEGORY_NAMES.get(contractCategory));
        result.put("rows", rows);
        result.put("basis", "成本账户「实际成本」维度的全部变动流水（幂等记账留痕，最多 200 条）；"
                + "ROLLUP=归集任务对账差额，SEED=演示种子，MANUAL=人工调整，均带来源单据可追");
        result.put("attachmentNote", ATTACHMENT_NOTE);
        return result;
    }

    // ==================== L4：供应商 → 合同 ====================

    /**
     * 供应商名下该类合同清单（supplierName 为空 = 该类全部合同，供不带供应商维度的入口用）。
     * <p>supplierName 与聚合列同源（同一 COALESCE 表达式），保证「从聚合行点进来一定筛得出」。</p>
     */
    public Map<String, Object> getContracts(Long projectId, String contractCategory, String supplierName) {
        requireNonNull(projectId, "项目ID不能为空");
        validateCategory(contractCategory);
        String supplier = (supplierName == null || supplierName.isBlank()) ? null : supplierName.trim();
        List<Map<String, Object>> rows = switch (contractCategory) {
            case "PURCHASE" -> drillDownMapper.contractsPurchase(projectId, supplier);
            case "LABOR" -> drillDownMapper.contractsLabor(projectId, supplier);
            case "MACHINE" -> drillDownMapper.contractsMachine(projectId, supplier);
            case "SUBCONTRACT" -> drillDownMapper.contractsSubcontract(projectId, supplier);
            default -> drillDownMapper.contractsOther(projectId, supplier);
        };

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("contractCategory", contractCategory);
        result.put("supplierName", supplier);
        result.put("categoryName", CATEGORY_NAMES.get(contractCategory));
        result.put("rows", rows);
        result.put("attachmentNote", ATTACHMENT_NOTE);
        // 机械/分包/其他合同表无流程实例列（实证）：合同级审批轨迹不可得，付款单据级可得
        if ("MACHINE".equals(contractCategory) || "SUBCONTRACT".equals(contractCategory)
                || "OTHER_EXPENSE".equals(contractCategory)) {
            result.put("note", "该类合同表无审批流程实例列，合同本身无审批轨迹；请下钻到付款/结算单据查看审批链");
        }
        return result;
    }

    // ==================== L5：合同 → 原始单据 ====================

    /**
     * 合同构成单据：结算单 + 付款申请 + 收票登记（+ 采购类的入库单关联已在结算行带出）。
     * <p>付款/收票按 contract_category 精确路由，防跨表 ID 撞号错配；
     * OTHER_EXPENSE 兼容历史空值（付款申请该字段允许 NULL，向后兼容路由见
     * {@code PaymentApplyService.resolvePayable}）。</p>
     */
    public Map<String, Object> getContractDocs(String contractCategory, Long contractId) {
        validateCategory(contractCategory);
        requireNonNull(contractId, "合同ID不能为空");

        List<Map<String, Object>> docs = new ArrayList<>();

        // 结算单（OTHER_EXPENSE 无结算单表）
        List<Map<String, Object>> settlements = switch (contractCategory) {
            case "PURCHASE" -> drillDownMapper.settlementsPurchase(contractId);
            case "LABOR" -> drillDownMapper.settlementsLabor(contractId);
            case "MACHINE" -> drillDownMapper.settlementsMachine(contractId);
            case "SUBCONTRACT" -> drillDownMapper.settlementsSubcontract(contractId);
            default -> List.of();
        };
        for (Map<String, Object> s : settlements) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("docType", "SETTLEMENT");
            doc.put("docTypeName", "结算单");
            doc.put("docId", s.get("id"));
            doc.put("docNo", s.get("docNo"));
            doc.put("docDate", s.get("docDate"));
            doc.put("dateLabel", s.get("docNo") != null ? "结算日期" : "创建时间");
            doc.put("amount", s.get("amount"));
            doc.put("status", s.get("status"));
            doc.put("workflowInstanceId", s.get("workflowInstanceId"));
            doc.put("refInboundCode", s.get("refInboundCode"));
            doc.put("refInboundAmount", s.get("refInboundAmount"));
            docs.add(doc);
        }

        // 付款申请
        LambdaQueryWrapper<BizPaymentApply> payQuery = new LambdaQueryWrapper<BizPaymentApply>()
                .eq(BizPaymentApply::getContractId, contractId);
        applyCategoryFilter(payQuery, contractCategory,
                BizPaymentApply::getContractCategory, true);
        List<BizPaymentApply> payments = paymentApplyMapper.selectList(payQuery);
        for (BizPaymentApply p : payments) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("docType", "PAYMENT");
            doc.put("docTypeName", "付款申请");
            doc.put("docId", p.getId());
            doc.put("docNo", null); // 表无付款单号列（实证），前端以「单据#id」呈现，不合成假编号
            doc.put("docDate", p.getPaymentDate() != null ? p.getPaymentDate() : p.getCreatedAt());
            doc.put("dateLabel", p.getPaymentDate() != null ? "计划付款日期" : "创建时间");
            doc.put("amount", p.getPaymentAmount());
            doc.put("status", p.getStatus());
            doc.put("payStatus", p.getPayStatus());
            doc.put("supplierName", p.getSupplierName());
            doc.put("workflowInstanceId", p.getWorkflowInstanceId());
            docs.add(doc);
        }

        // 收票登记
        LambdaQueryWrapper<BizInvoiceReceived> invQuery = new LambdaQueryWrapper<BizInvoiceReceived>()
                .eq(BizInvoiceReceived::getContractId, contractId);
        applyCategoryFilter(invQuery, contractCategory,
                BizInvoiceReceived::getContractCategory, false);
        List<BizInvoiceReceived> invoices = invoiceReceivedMapper.selectList(invQuery);
        for (BizInvoiceReceived inv : invoices) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("docType", "INVOICE");
            doc.put("docTypeName", "收票登记");
            doc.put("docId", inv.getId());
            doc.put("docNo", null); // 表无发票号列（实证），不伪造单号
            doc.put("docDate", inv.getInvoiceDate());
            doc.put("dateLabel", "收票日期");
            doc.put("amount", inv.getInvoiceAmount());
            doc.put("status", inv.getStatus());
            doc.put("supplierName", inv.getSupplierName());
            doc.put("workflowInstanceId", null);
            docs.add(doc);
        }

        // 采购合同补充入库单（§13 采购→入库环节）
        if ("PURCHASE".equals(contractCategory)) {
            List<BizMaterialInbound> inbounds = materialInboundMapper.selectList(
                    new LambdaQueryWrapper<BizMaterialInbound>()
                            .eq(BizMaterialInbound::getContractId, contractId)
                            .orderByDesc(BizMaterialInbound::getInboundDate));
            for (BizMaterialInbound in : inbounds) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("docType", "INBOUND");
                doc.put("docTypeName", "入库单");
                doc.put("docId", in.getId());
                doc.put("docNo", in.getInboundCode());
                doc.put("docDate", in.getInboundDate());
                doc.put("dateLabel", "入库日期");
                doc.put("amount", in.getTotalAmount());
                doc.put("status", in.getStatus());
                doc.put("workflowInstanceId", null);
                docs.add(doc);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractCategory", contractCategory);
        result.put("categoryName", CATEGORY_NAMES.get(contractCategory));
        result.put("contractId", contractId);
        result.put("rows", docs);
        result.put("attachmentNote", ATTACHMENT_NOTE);
        if ("OTHER_EXPENSE".equals(contractCategory)) {
            result.put("note", "其他支出合同无结算单表，付款走付款申请/其他费用付款");
        }
        return result;
    }

    // ==================== 私有 ====================

    /** 类别 → 供应商聚合行（各表列名差异已在 Mapper 归一） */
    private List<Map<String, Object>> querySuppliers(Long projectId, String contractCategory) {
        return switch (contractCategory) {
            case "PURCHASE" -> drillDownMapper.supplierAggPurchase(projectId);
            case "LABOR" -> drillDownMapper.supplierAggLabor(projectId);
            case "MACHINE" -> drillDownMapper.supplierAggMachine(projectId);
            case "SUBCONTRACT" -> drillDownMapper.supplierAggSubcontract(projectId);
            default -> drillDownMapper.supplierAggOther(projectId);
        };
    }

    /**
     * contract_category 过滤：模块类别精确匹配；OTHER_EXPENSE 兼容
     * OTHER_INCOME/NULL 历史值（与付款回写路由同一判据，见 PaymentApplyService.MODULE_CATEGORIES）。
     */
    private <T> void applyCategoryFilter(LambdaQueryWrapper<T> wrapper, String contractCategory,
                                         com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> categoryGetter,
                                         boolean otherIncludesNull) {
        if (DRILL_CATEGORIES.contains(contractCategory) && !"OTHER_EXPENSE".equals(contractCategory)) {
            wrapper.eq(categoryGetter, contractCategory);
        } else {
            wrapper.and(w -> {
                w.in(categoryGetter, "OTHER_EXPENSE", "OTHER_INCOME");
                if (otherIncludesNull) {
                    w.or().isNull(categoryGetter);
                }
            });
        }
    }

    private void validateCategory(String contractCategory) {
        if (contractCategory == null || !DRILL_CATEGORIES.contains(contractCategory)) {
            throw new BusinessException(400,
                    "合同类别不合法，可选值: " + DRILL_CATEGORIES);
        }
    }

    private void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new BusinessException(400, message);
        }
    }

    private BigDecimal sum(List<BizCostAccount> list,
                           java.util.function.Function<BizCostAccount, BigDecimal> getter) {
        BigDecimal total = BigDecimal.ZERO;
        for (BizCostAccount a : list) {
            BigDecimal v = getter.apply(a);
            total = total.add(v != null ? v : BigDecimal.ZERO);
        }
        return total;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
