package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.domain.BizCostAccountTxn;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.budget.mapper.BizCostAccountTxnMapper;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CBS 成本流水服务 —— 成本账户金额变动的<b>唯一入口</b>。
 *
 * <h3>为什么要有这一层</h3>
 * <p>
 * 若各业务模块（合同、采购、结算、付款、变更）各自 UPDATE 成本账户金额，会出现三类经典事故：
 * </p>
 * <ol>
 *   <li><b>重复记账</b>：消息重投/接口重试导致同一笔业务加两次钱，且无从发现；</li>
 *   <li><b>口径分裂</b>：A 模块把付款记进 actual，B 模块把结算记进 actual，同一维度两套语义；</li>
 *   <li><b>不可追溯</b>：余额变了但没人知道为什么变，审计与对账只能靠翻日志猜。</li>
 * </ol>
 * <p>
 * 本服务把「金额变动」收敛为一个带幂等键的记账动作：<b>先查/插流水（幂等锁），再原子调整余额</b>，
 * 两步同事务。任何模块要改成本账户金额，只能通过这里，并必须说明来源。
 * </p>
 *
 * <h3>幂等保证</h3>
 * <p>
 * 幂等键 = (sourceType, sourceId, accountId, amountType)，DB 唯一索引兜底。
 * 重复调用返回 {@link PostResult#DUPLICATE}，<b>不抛异常</b>——
 * 因为对调用方（Outbox 处理器）而言「已经记过了」就是成功，抛异常会触发无意义的重试直至死信。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostLedgerService {

    /** 金额维度 */
    public static final String AMT_BASELINE = "BASELINE";
    public static final String AMT_CURRENT = "CURRENT";
    public static final String AMT_COMMITMENT = "COMMITMENT";
    public static final String AMT_ACTUAL = "ACTUAL";
    public static final String AMT_FORECAST = "FORECAST";

    /** 来源类型 */
    public static final String SRC_CHANGE_EVENT = "CHANGE_EVENT";
    public static final String SRC_CONTRACT = "CONTRACT";
    public static final String SRC_PURCHASE = "PURCHASE";
    public static final String SRC_SETTLEMENT = "SETTLEMENT";
    public static final String SRC_PAYMENT = "PAYMENT";
    public static final String SRC_MATERIAL = "MATERIAL";
    public static final String SRC_MANUAL = "MANUAL";
    /**
     * 自动归集对账。
     * <p>
     * 与 MANUAL 严格区分：MANUAL 是人工直接改数（需追责），
     * ROLLUP 是从源单据自动汇总后的对账差额（可重跑、可解释）。
     * 混用会让审计无法分辨「这笔调整是人改的还是系统算的」。
     * </p>
     */
    public static final String SRC_ROLLUP = "ROLLUP";

    private final BizCostAccountMapper costAccountMapper;
    private final BizCostAccountTxnMapper txnMapper;

    /** 记账结果 */
    public enum PostResult {
        /** 已记账，余额已调整 */
        POSTED,
        /** 幂等命中：该业务事实此前已记账，本次未产生任何变更 */
        DUPLICATE
    }

    /**
     * 记账请求（不可变参数对象，避免 7~8 个位置参数传错顺序）。
     */
    public record PostCommand(
            Long accountId,
            String amountType,
            BigDecimal deltaAmount,
            String sourceType,
            String sourceId,
            String sourceNumber,
            LocalDateTime occurredAt,
            String remark) {

        /** 校验必填项，非法直接抛业务异常（写入侧严格） */
        public void validate() {
            if (accountId == null) {
                throw new BusinessException("成本账户ID不能为空");
            }
            if (amountType == null || amountType.isBlank()) {
                throw new BusinessException("金额维度不能为空");
            }
            if (!List.of(AMT_BASELINE, AMT_CURRENT, AMT_COMMITMENT, AMT_ACTUAL, AMT_FORECAST)
                    .contains(amountType)) {
                throw new BusinessException("非法金额维度：" + amountType);
            }
            if (deltaAmount == null) {
                throw new BusinessException("变动金额不能为空（无变动请传 0 或直接跳过记账）");
            }
            if (sourceType == null || sourceType.isBlank()) {
                throw new BusinessException("来源类型不能为空（成本变动必须可溯源）");
            }
            if (sourceId == null || sourceId.isBlank()) {
                throw new BusinessException("来源业务ID不能为空（幂等键依赖它）");
            }
        }
    }

    /**
     * 记账：写流水（幂等锁）+ 原子调整余额，同事务。
     *
     * @param cmd 记账命令
     * @return POSTED=已记账；DUPLICATE=幂等命中未变更
     */
    @Transactional(rollbackFor = Exception.class)
    public PostResult post(PostCommand cmd) {
        cmd.validate();

        if (cmd.deltaAmount().signum() == 0) {
            // 零变动不落流水：否则台账里全是噪声，掩盖真实变动
            log.debug("零变动跳过记账，accountId={}, source={}:{}",
                    cmd.accountId(), cmd.sourceType(), cmd.sourceId());
            return PostResult.POSTED;
        }

        // 1. 幂等探针置于账户校验之前：
        //    已记过账就是「成功」，与账户当前是否被锁定/关闭无关。
        //    若先做可写性校验，账户在首次记账后被 CLOSE，重试就会以「账户已关闭」
        //    失败并一路退避到死信——而那笔账其实早已正确入账。
        if (txnMapper.countBySource(cmd.sourceType(), cmd.sourceId(),
                cmd.accountId(), cmd.amountType()) > 0) {
            log.info("成本流水幂等命中，跳过重复记账，source={}:{}, accountId={}, amountType={}",
                    cmd.sourceType(), cmd.sourceId(), cmd.accountId(), cmd.amountType());
            return PostResult.DUPLICATE;
        }

        BizCostAccount account = costAccountMapper.selectById(cmd.accountId());
        if (account == null) {
            throw new BusinessException("成本账户不存在：" + cmd.accountId());
        }
        assertAccountWritable(account, cmd.amountType());

        // 2. 原子调整余额（SQL 自增，避免读-改-写并发覆盖；带非负约束）
        int affected = applyDelta(cmd.accountId(), cmd.amountType(), cmd.deltaAmount());
        if (affected == 0) {
            // 唯一成因：调整后余额将为负（账户不存在已在上一步排除）
            throw new BusinessException(String.format(
                    "成本账户[%s]的%s调整后将为负数，已拒绝；当前余额=%s，变动=%s",
                    account.getAccountCode(), displayName(cmd.amountType()),
                    currentBalance(account, cmd.amountType()), cmd.deltaAmount()));
        }

        // 3. 落流水（余额取调整后的最新值，保证 balance_after 与库内一致）
        BizCostAccount after = costAccountMapper.selectById(cmd.accountId());
        BizCostAccountTxn txn = new BizCostAccountTxn();
        txn.setProjectId(account.getProjectId());
        txn.setAccountId(cmd.accountId());
        txn.setAmountType(cmd.amountType());
        txn.setDeltaAmount(cmd.deltaAmount());
        txn.setBalanceAfter(currentBalance(after, cmd.amountType()));
        txn.setSourceType(cmd.sourceType());
        txn.setSourceId(cmd.sourceId());
        txn.setSourceNumber(cmd.sourceNumber());
        txn.setOccurredAt(cmd.occurredAt() != null ? cmd.occurredAt() : LocalDateTime.now());
        txn.setRemark(cmd.remark());

        try {
            txnMapper.insert(txn);
        } catch (DuplicateKeyException dup) {
            // 并发下另一实例已抢先记账：回滚本次余额调整，按幂等语义返回 DUPLICATE
            log.info("成本流水并发幂等命中，回滚本次调整，source={}:{}, accountId={}",
                    cmd.sourceType(), cmd.sourceId(), cmd.accountId());
            throw new RollbackDuplicateException();
        }

        log.info("成本流水已记账，accountId={}, amountType={}, delta={}, source={}:{}, balanceAfter={}",
                cmd.accountId(), cmd.amountType(), cmd.deltaAmount(),
                cmd.sourceType(), cmd.sourceId(), txn.getBalanceAfter());
        return PostResult.POSTED;
    }

    /**
     * 幂等命中的事务回滚信号。
     * <p>
     * 并发下两个实例同时通过探针、同时调整了余额，后者插入流水时撞唯一键。
     * 此时必须回滚余额调整（否则重复记账），但对调用方仍应表现为「幂等成功」。
     * 由 {@link #postPostCommandSafely} 捕获转换。
     * </p>
     */
    public static class RollbackDuplicateException extends RuntimeException {
        public RollbackDuplicateException() {
            super("成本流水并发幂等命中");
        }
    }

    /**
     * 记账（并发安全包装）：把并发幂等冲突转成 DUPLICATE 返回值。
     * <p>
     * 必须用 {@code REQUIRES_NEW} 让回滚只影响本次记账，不牵连调用方事务。
     * </p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PostResult postPostCommandSafely(PostCommand cmd) {
        try {
            return post(cmd);
        } catch (RollbackDuplicateException dup) {
            return PostResult.DUPLICATE;
        }
    }

    /**
     * 批量记账（同一来源影响多个账户，如一次变更事件调整多个 CBS 账户）。
     * <p>整批同事务：任一账户失败则全部回滚，避免「变更记了一半」的中间态。</p>
     *
     * @return 实际记账条数（不含幂等命中）
     */
    @Transactional(rollbackFor = Exception.class)
    public int postBatch(List<PostCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return 0;
        }
        int posted = 0;
        for (PostCommand cmd : commands) {
            if (post(cmd) == PostResult.POSTED) {
                posted++;
            }
        }
        return posted;
    }

    // ==================== 查询 ====================

    /**
     * 查询账户流水（成本下钻）。
     */
    public List<BizCostAccountTxn> listByAccount(Long accountId, String amountType,
                                                 LocalDateTime from, LocalDateTime to) {
        if (accountId == null) {
            throw new BusinessException("成本账户ID不能为空");
        }
        return txnMapper.listByAccount(accountId, amountType, from, to);
    }

    /**
     * 分页查询流水。
     */
    public IPage<BizCostAccountTxn> page(int page, int size, Long accountId,
                                         String amountType, String sourceType) {
        Page<BizCostAccountTxn> p = new Page<>(page, size);
        return txnMapper.pageByAccount(p, accountId, amountType, sourceType);
    }

    /**
     * 按来源汇总（如某项目全部变更事件对 CURRENT 的累计影响）。
     */
    public BigDecimal sumBySource(Long projectId, String sourceType, String amountType) {
        BigDecimal sum = txnMapper.sumDeltaBySource(projectId, sourceType, amountType);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    // ==================== 内部工具 ====================

    /**
     * 按金额维度分派到对应的原子调整 SQL。
     */
    private int applyDelta(Long accountId, String amountType, BigDecimal delta) {
        return switch (amountType) {
            case AMT_CURRENT -> costAccountMapper.adjustCurrentAmount(accountId, delta);
            case AMT_COMMITMENT -> costAccountMapper.adjustCommitmentAmount(accountId, delta);
            case AMT_ACTUAL -> costAccountMapper.adjustActualAmount(accountId, delta);
            // BASELINE / FORECAST 走「设置绝对值」语义（见 setAmount），不做增量调整：
            // 目标成本一经批准只能通过正式变更覆盖，预测则整体重算，二者都不适合累加。
            case AMT_BASELINE, AMT_FORECAST -> throw new BusinessException(
                    "金额维度[" + amountType + "]不支持增量记账，请使用 setAmount 覆盖");
            default -> throw new BusinessException("非法金额维度：" + amountType);
        };
    }

    /**
     * 覆盖式设置金额（BASELINE / FORECAST 专用），同样落流水保证可追溯。
     *
     * @param accountId  账户ID
     * @param amountType 仅允许 BASELINE / FORECAST
     * @param newValue   新值（绝对值）
     * @param sourceType 来源类型
     * @param sourceId   来源业务ID（幂等键）
     */
    @Transactional(rollbackFor = Exception.class)
    public PostResult setAmount(Long accountId, String amountType, BigDecimal newValue,
                                String sourceType, String sourceId, String remark) {
        if (!AMT_BASELINE.equals(amountType) && !AMT_FORECAST.equals(amountType)) {
            throw new BusinessException("覆盖式设置仅支持 BASELINE / FORECAST，当前：" + amountType);
        }
        if (newValue == null || newValue.signum() < 0) {
            throw new BusinessException("金额不能为空且不得为负");
        }

        // 幂等探针同样前置：已设置过即视为成功，不受账户后续锁定/关闭影响
        if (txnMapper.countBySource(sourceType, sourceId, accountId, amountType) > 0) {
            return PostResult.DUPLICATE;
        }

        BizCostAccount account = costAccountMapper.selectById(accountId);
        if (account == null) {
            throw new BusinessException("成本账户不存在：" + accountId);
        }
        assertAccountWritable(account, amountType);

        BigDecimal oldValue = currentBalance(account, amountType);
        BigDecimal delta = newValue.subtract(oldValue);

        BizCostAccount patch = new BizCostAccount();
        patch.setId(accountId);
        if (AMT_BASELINE.equals(amountType)) {
            patch.setBaselineAmount(newValue);
        } else {
            patch.setForecastAmount(newValue);
        }
        costAccountMapper.updateById(patch);

        BizCostAccountTxn txn = new BizCostAccountTxn();
        txn.setProjectId(account.getProjectId());
        txn.setAccountId(accountId);
        txn.setAmountType(amountType);
        txn.setDeltaAmount(delta);
        txn.setBalanceAfter(newValue);
        txn.setSourceType(sourceType);
        txn.setSourceId(sourceId);
        txn.setOccurredAt(LocalDateTime.now());
        txn.setRemark(remark);
        try {
            txnMapper.insert(txn);
        } catch (DuplicateKeyException dup) {
            log.info("覆盖式设置并发幂等命中，accountId={}, source={}:{}", accountId, sourceType, sourceId);
            return PostResult.DUPLICATE;
        }

        log.info("成本账户{}已覆盖设置，accountId={}, {}→{}, source={}:{}",
                displayName(amountType), accountId, oldValue, newValue, sourceType, sourceId);
        return PostResult.POSTED;
    }

    /**
     * 账户可写性校验：锁定/关闭的账户拒绝金额变动。
     * <p>
     * 项目结案后账户会被 CLOSE，此时若还允许结算回写，账就会在「已定案」之后悄悄变化，
     * 这是审计最不能接受的情况——必须显式解锁才能改。
     * </p>
     */
    private void assertAccountWritable(BizCostAccount account, String amountType) {
        String status = account.getStatus();
        if ("CLOSED".equals(status)) {
            throw new BusinessException(String.format(
                    "成本账户[%s]已关闭（项目结案），禁止%s变动；如需调整请先走解锁流程",
                    account.getAccountCode(), displayName(amountType)));
        }
        if ("LOCKED".equals(status) && !AMT_ACTUAL.equals(amountType)) {
            // 锁定语义：冻结预算口径（baseline/current/commitment/forecast），
            // 但实际成本仍须允许回写——否则已发生支出无处入账，账实必然不符。
            throw new BusinessException(String.format(
                    "成本账户[%s]已锁定，禁止%s变动（实际成本仍可回写）",
                    account.getAccountCode(), displayName(amountType)));
        }
    }

    private BigDecimal currentBalance(BizCostAccount account, String amountType) {
        if (account == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal v = switch (amountType) {
            case AMT_BASELINE -> account.getBaselineAmount();
            case AMT_CURRENT -> account.getCurrentAmount();
            case AMT_COMMITMENT -> account.getCommitmentAmount();
            case AMT_ACTUAL -> account.getActualAmount();
            case AMT_FORECAST -> account.getForecastAmount();
            default -> BigDecimal.ZERO;
        };
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String displayName(String amountType) {
        return switch (amountType) {
            case AMT_BASELINE -> "目标成本";
            case AMT_CURRENT -> "当前预算";
            case AMT_COMMITMENT -> "已承诺";
            case AMT_ACTUAL -> "实际成本";
            case AMT_FORECAST -> "完工预测";
            default -> amountType;
        };
    }

    /**
     * 按项目+来源查询流水（供跨模块对账，如核对某合同累计已记账金额）。
     */
    public List<BizCostAccountTxn> listByProjectAndSource(Long projectId, String sourceType, String sourceId) {
        return txnMapper.selectList(new LambdaQueryWrapper<BizCostAccountTxn>()
                .eq(BizCostAccountTxn::getProjectId, projectId)
                .eq(BizCostAccountTxn::getSourceType, sourceType)
                .eq(sourceId != null && !sourceId.isBlank(), BizCostAccountTxn::getSourceId, sourceId)
                .orderByDesc(BizCostAccountTxn::getOccurredAt));
    }
}
