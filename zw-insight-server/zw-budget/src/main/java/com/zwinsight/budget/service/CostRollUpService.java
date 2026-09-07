package com.zwinsight.budget.service;

import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.domain.BizCostAccountLink;
import com.zwinsight.budget.mapper.BizCostAccountLinkMapper;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.budget.mapper.CostRollUpMapper;
import com.zwinsight.common.exception.BusinessException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本归集服务 —— 把分散在各业务模块的承诺额/实际成本收敛到 CBS 成本账户。
 *
 * <h3>补齐的断点</h3>
 * <p>
 * 此前成本账户的 commitment/actual 只能靠人工调 {@code /sync} 接口，等于把
 * 「合同签了多少、结算了多少」这件事重新抄一遍——违反「禁止重复录入业务事实」。
 * 本服务直接读源单据（合同/结算/材料出库）自动归集，业务模块只需照常审批，
 * 成本主线自动跟上。
 * </p>
 *
 * <h3>成本口径（关键业务约定）</h3>
 * <ul>
 *   <li><b>承诺 COMMITMENT</b> = 有效合同金额（签订即占用额度，未付款也已锁定）</li>
 *   <li><b>实际 ACTUAL</b> = 已审批结算 + 材料出库消耗</li>
 * </ul>
 * <p>
 * <b>实际成本 ≠ 付款</b>：付款是现金流时点，结算是成本确认时点。
 * 用付款当成本会让「已付款未结算」虚增成本、「已结算未付款」漏记成本，
 * 两者都使项目核算失真——这是施工 ERP 最经典的口径错误。
 * </p>
 *
 * <h3>账户解析：歧义不猜</h3>
 * <ol>
 *   <li>单据已有显式绑定（{@code biz_cost_account_link}）→ 按绑定归集</li>
 *   <li>该科目下只有一个 CBS 账户 → 自动归集（无歧义）</li>
 *   <li>其余 → 记入 {@code unmapped} 报告，等业务人员在 UI 上绑定</li>
 * </ol>
 * <p>
 * 绝不按比例自动分摊：分摊错会造成成本归属错误，进而导出错误的项目盈亏结论，
 * 危害远大于「暂时少归集一笔」。
 * </p>
 *
 * <h3>幂等：以「状态跃迁」为键</h3>
 * <p>
 * 归集算出的是<b>目标绝对值</b>，与库内当前值做差得到 delta，幂等键编码为
 * {@code ROLLUP:{accountId}:{amountType}:{from}->{to}}：
 * </p>
 * <ul>
 *   <li>重复跑：from 已等于 to → delta=0 → 不写流水</li>
 *   <li>并发跑同一跃迁：唯一键冲突 → DUPLICATE，不会双记</li>
 *   <li>源单据后续变化：产生新的 from→to 跃迁 → 新键 → 正常记账</li>
 * </ul>
 * <p>因此本服务可安全地重复执行、定时执行、手工触发，结果始终收敛到正确值。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostRollUpService {

    // ==================== 来源类型（写入流水的 source_type，与 CostLedgerService.SRC_* 对齐） ====================
    private static final String SRC_CONTRACT = CostLedgerService.SRC_CONTRACT;
    private static final String SRC_SETTLEMENT = CostLedgerService.SRC_SETTLEMENT;
    private static final String SRC_MATERIAL = CostLedgerService.SRC_MATERIAL;

    /** 归集专用幂等键前缀，与单据级记账的键空间隔离 */
    private static final String ROLLUP_KEY_PREFIX = "ROLLUP";

    /** 费用类别常量（与 biz_cost_account.cost_category 枚举一致） */
    private static final String CAT_MATERIAL = "MATERIAL";
    private static final String CAT_LABOR = "LABOR";
    private static final String CAT_MACHINE = "MACHINE";
    private static final String CAT_SUBCONTRACT = "SUBCONTRACT";

    private final CostRollUpMapper rollUpMapper;
    private final BizCostAccountMapper costAccountMapper;
    private final BizCostAccountLinkMapper linkMapper;
    private final CostLedgerService costLedgerService;

    // ==================== 对外入口 ====================

    /**
     * 执行归集并写入成本流水。
     *
     * @param projectId 项目ID
     * @return 归集报告（含成功记账数、未归集单据清单、各账户目标值）
     */
    @Transactional(rollbackFor = Exception.class)
    public RollupReport rollup(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }

        List<BizCostAccount> accounts = costAccountMapper.selectByProject(projectId);
        if (accounts.isEmpty()) {
            // 无账户不是错误（项目可能还没建 CBS），但要明确告知调用方「什么都没做」
            RollupReport empty = new RollupReport();
            empty.setProjectId(projectId);
            empty.setMessage("该项目尚未建立 CBS 成本账户，归集未执行");
            return empty;
        }

        // 一次性载入显式绑定，按 sourceType:sourceId 建索引，避免逐单据查库
        Map<String, Long> bindingIndex = new LinkedHashMap<>();
        for (BizCostAccountLink link : linkMapper.selectByProject(projectId)) {
            bindingIndex.put(bindingKey(link.getSourceType(), link.getSourceId()), link.getAccountId());
        }

        RollupReport report = new RollupReport();
        report.setProjectId(projectId);

        // 各账户的目标绝对值：accountId → [commitmentTarget, actualTarget]
        Map<Long, BigDecimal[]> targets = new LinkedHashMap<>();
        for (BizCostAccount a : accounts) {
            targets.put(a.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        // ---- 承诺额：有效合同 ----
        allocate(projectId, CAT_MATERIAL, SRC_CONTRACT, "PURCHASE_CONTRACT",
                rollUpMapper.listPurchaseContracts(projectId), accounts, bindingIndex, targets, report, 0);
        allocate(projectId, CAT_LABOR, SRC_CONTRACT, "LABOR_CONTRACT",
                rollUpMapper.listLaborContracts(projectId), accounts, bindingIndex, targets, report, 0);
        allocate(projectId, CAT_MACHINE, SRC_CONTRACT, "MACHINE_CONTRACT",
                rollUpMapper.listMachineContracts(projectId), accounts, bindingIndex, targets, report, 0);
        allocate(projectId, CAT_SUBCONTRACT, SRC_CONTRACT, "SUBCONTRACT_CONTRACT",
                rollUpMapper.listSubcontracts(projectId), accounts, bindingIndex, targets, report, 0);

        // ---- 实际成本：已审批结算 + 材料出库消耗 ----
        allocate(projectId, CAT_MATERIAL, SRC_SETTLEMENT, "PURCHASE_SETTLEMENT",
                rollUpMapper.listPurchaseSettlements(projectId), accounts, bindingIndex, targets, report, 1);
        allocate(projectId, CAT_MATERIAL, SRC_MATERIAL, "MATERIAL_OUTBOUND",
                rollUpMapper.listMaterialOutbounds(projectId), accounts, bindingIndex, targets, report, 1);
        allocate(projectId, CAT_LABOR, SRC_SETTLEMENT, "LABOR_SETTLEMENT",
                rollUpMapper.listLaborSettlements(projectId), accounts, bindingIndex, targets, report, 1);
        allocate(projectId, CAT_MACHINE, SRC_SETTLEMENT, "MACHINE_WORK_SETTLEMENT",
                rollUpMapper.listMachineSettlements(projectId), accounts, bindingIndex, targets, report, 1);
        allocate(projectId, CAT_SUBCONTRACT, SRC_SETTLEMENT, "SUBCONTRACT_SETTLEMENT",
                rollUpMapper.listSubcontractSettlements(projectId), accounts, bindingIndex, targets, report, 1);

        // ---- 目标值 vs 库内当前值 → 差额记账 ----
        for (BizCostAccount account : accounts) {
            BigDecimal[] target = targets.get(account.getId());
            if (target == null) {
                continue;
            }
            syncAccount(account, CostLedgerService.AMT_COMMITMENT,
                    nvl(account.getCommitmentAmount()), target[0], report);
            syncAccount(account, CostLedgerService.AMT_ACTUAL,
                    nvl(account.getActualAmount()), target[1], report);
        }

        report.setMessage(String.format("归集完成：记账 %d 笔，幂等跳过 %d 笔，待绑定单据 %d 张",
                report.getPostedCount(), report.getDuplicateCount(), report.getUnmapped().size()));
        log.info("成本归集完成，projectId={}, {}", projectId, report.getMessage());
        return report;
    }

    // ==================== 内部：单据分配 ====================

    /**
     * 把一批源单据分配到成本账户。
     *
     * @param slotIndex 0=承诺额，1=实际成本（targets 数组下标）
     */
    private void allocate(Long projectId, String category, String ledgerSourceType, String bindingSourceType,
                          List<CostRollUpMapper.SourceDoc> docs, List<BizCostAccount> accounts,
                          Map<String, Long> bindingIndex, Map<Long, BigDecimal[]> targets,
                          RollupReport report, int slotIndex) {
        if (docs == null || docs.isEmpty()) {
            return;
        }

        // 该科目下的候选账户（显式绑定优先，其次单一账户自动归集）
        List<BizCostAccount> candidates = new ArrayList<>();
        for (BizCostAccount a : accounts) {
            if (category.equals(a.getCostCategory())) {
                candidates.add(a);
            }
        }

        for (CostRollUpMapper.SourceDoc doc : docs) {
            BigDecimal amount = doc.safeAmount();
            Long accountId = bindingIndex.get(bindingKey(bindingSourceType, String.valueOf(doc.getSourceId())));

            if (accountId == null && candidates.size() == 1) {
                // 科目下唯一账户：无歧义，自动归集
                accountId = candidates.get(0).getId();
            }

            if (accountId == null) {
                // 无绑定且存在歧义（0 个或多个同科目账户）→ 报告待人工绑定，不猜
                UnmappedDoc unmapped = new UnmappedDoc();
                unmapped.setCategory(category);
                unmapped.setSourceType(bindingSourceType);
                unmapped.setSourceId(String.valueOf(doc.getSourceId()));
                unmapped.setSourceNumber(doc.getSourceNumber());
                unmapped.setAmount(amount);
                unmapped.setReason(candidates.isEmpty()
                        ? "该项目下无「" + category + "」科目的成本账户"
                        : "该项目下「" + category + "」科目有 " + candidates.size() + " 个成本账户，需显式绑定");
                report.getUnmapped().add(unmapped);
                continue;
            }

            BigDecimal[] target = targets.get(accountId);
            if (target == null) {
                // 绑定指向了不属于本项目的账户（脏数据），显式暴露而非静默丢弃
                log.warn("归集发现绑定指向未知账户，projectId={}, accountId={}, source={}:{}",
                        projectId, accountId, bindingSourceType, doc.getSourceId());
                UnmappedDoc bad = new UnmappedDoc();
                bad.setCategory(category);
                bad.setSourceType(bindingSourceType);
                bad.setSourceId(String.valueOf(doc.getSourceId()));
                bad.setSourceNumber(doc.getSourceNumber());
                bad.setAmount(amount);
                bad.setReason("绑定指向的成本账户不属于本项目或已删除（accountId=" + accountId + "）");
                report.getUnmapped().add(bad);
                continue;
            }

            target[slotIndex] = target[slotIndex].add(amount);
            report.setSourceDocCount(report.getSourceDocCount() + 1);
        }
    }

    /**
     * 单账户单维度对账：目标绝对值 vs 当前值，差额走幂等记账。
     */
    private void syncAccount(BizCostAccount account, String amountType,
                             BigDecimal current, BigDecimal target, RollupReport report) {
        BigDecimal delta = target.subtract(current);
        if (delta.signum() == 0) {
            return;
        }

        // 幂等键编码「状态跃迁」：from→to。重复跑时 from 已等于 to，delta=0 不会到这；
        // 并发跑同一跃迁时唯一键冲突 → DUPLICATE，杜绝双记。
        String sourceId = String.format("%s:%d:%s:%s->%s",
                ROLLUP_KEY_PREFIX, account.getId(), amountType,
                current.toPlainString(), target.toPlainString());

        CostLedgerService.PostCommand cmd = new CostLedgerService.PostCommand(
                account.getId(),
                amountType,
                delta,
                CostLedgerService.SRC_ROLLUP,
                sourceId,
                account.getAccountCode(),
                LocalDateTime.now(),
                "成本归集对账（源单据自动汇总）");

        try {
            CostLedgerService.PostResult result = costLedgerService.post(cmd);
            if (result == CostLedgerService.PostResult.POSTED) {
                report.setPostedCount(report.getPostedCount() + 1);
                AccountSync synced = new AccountSync();
                synced.setAccountId(account.getId());
                synced.setAccountCode(account.getAccountCode());
                synced.setAccountName(account.getAccountName());
                synced.setAmountType(amountType);
                synced.setFrom(current);
                synced.setTo(target);
                synced.setDelta(delta);
                report.getSynced().add(synced);
            } else {
                report.setDuplicateCount(report.getDuplicateCount() + 1);
            }
        } catch (RuntimeException e) {
            // 单账户失败不阻断整体归集（如账户已锁定/关闭），但必须记入报告，不可静默
            log.warn("成本归集记账失败，accountId={}, amountType={}, delta={}, error={}",
                    account.getId(), amountType, delta, e.getMessage());
            FailedSync failed = new FailedSync();
            failed.setAccountId(account.getId());
            failed.setAccountCode(account.getAccountCode());
            failed.setAmountType(amountType);
            failed.setDelta(delta);
            failed.setReason(e.getMessage());
            report.getFailed().add(failed);
        }
    }

    private static String bindingKey(String sourceType, String sourceId) {
        return sourceType + ":" + sourceId;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ==================== 报告对象 ====================

    /** 归集报告（返回给调用方 / 前端展示） */
    @Data
    public static class RollupReport {
        private Long projectId;
        /** 汇总说明 */
        private String message = "";
        /** 参与归集的源单据数 */
        private int sourceDocCount;
        /** 实际记账笔数 */
        private int postedCount;
        /** 幂等命中跳过笔数 */
        private int duplicateCount;
        /** 成功对账明细 */
        private List<AccountSync> synced = new ArrayList<>();
        /** 未能归集的单据（需人工绑定） */
        private List<UnmappedDoc> unmapped = new ArrayList<>();
        /** 记账失败明细（如账户锁定） */
        private List<FailedSync> failed = new ArrayList<>();

        /** 是否存在待人工处理的项 */
        public boolean needsAttention() {
            return !unmapped.isEmpty() || !failed.isEmpty();
        }
    }

    /** 单账户单维度的对账结果 */
    @Data
    public static class AccountSync {
        private Long accountId;
        private String accountCode;
        private String accountName;
        private String amountType;
        private BigDecimal from;
        private BigDecimal to;
        private BigDecimal delta;
    }

    /** 未能归集的源单据 */
    @Data
    public static class UnmappedDoc {
        private String category;
        private String sourceType;
        private String sourceId;
        private String sourceNumber;
        private BigDecimal amount;
        private String reason;
    }

    /** 记账失败明细 */
    @Data
    public static class FailedSync {
        private Long accountId;
        private String accountCode;
        private String amountType;
        private BigDecimal delta;
        private String reason;
    }
}
