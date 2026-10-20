package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.event.finance.ExpenseSettlementPayableEvent;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizPayable;
import com.zwinsight.finance.domain.BizPayableWriteOff;
import com.zwinsight.finance.mapper.BizPayableMapper;
import com.zwinsight.finance.mapper.BizPayableWriteOffMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应付台账服务（V2026_115，对称 {@link ReceivableService}）
 * <p>
 * 生成：五类支出合同结算审批生效时按<b>本次结算金额</b>生成应付行
 * （由 {@code ExpenseSettlementPayableEvent} 驱动，幂等键 sourceType+sourceId+contractId；
 * 合同历史余额由迁移 BACKFILL 行覆盖，不重复计量）；
 * 核销：付款申请生效时按到期日 FIFO 冲减<b>同合同</b> OPEN 余额，逐笔落明细；
 * 冲减：结算单删除/作废时按原额冲减对应应付行（GREATEST 截断，不伪造负负债）；
 * 双写：每次生成/核销/冲减同事务原子增减 biz_project.payable_amount，
 * 维护不变量「项目应付单值 = 台账 OPEN 余额合计」。
 * </p>
 * <p>账龄口径与应收一致：NOT_DUE / 0-30 / 31-60 / 61-90 / OVER_90，
 * 逾期天数 = 今天 − 到期日。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayableService {

    private final BizPayableMapper payableMapper;
    private final BizPayableWriteOffMapper writeOffMapper;
    private final BizProjectMapper projectMapper;

    /** 默认账期天数（结算生效日 + N 天为应付到期日，可配置） */
    @Value("${zw.finance.payable.default-credit-days:30}")
    private int defaultCreditDays;

    private static final String[] AGING_BUCKETS = {"NOT_DUE", "D0_30", "D31_60", "D61_90", "OVER_90"};

    // ==================== 生成 / 冲减 ====================

    /**
     * 结算生效生成应付行（幂等：同 sourceType+sourceId+contractId 已存在则跳过）。
     * <p>应付金额 = 本次审批通过的结算金额；金额 ≤ 0 不生成（无负债事实不伪造）。</p>
     *
     * @param event 支出结算生效事件（发布方事务内同步消费）
     * @return 生成的应付行；金额≤0 或已存在时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public BizPayable generateFromSettlement(ExpenseSettlementPayableEvent event) {
        validateEvent(event);
        if (event.isReversed()) {
            throw new BusinessException("生成接口不接受冲销事件，请走 reduceOnSettlementReverse");
        }
        Long exists = payableMapper.selectCount(new LambdaQueryWrapper<BizPayable>()
                .eq(BizPayable::getSourceType, event.getSourceType())
                .eq(BizPayable::getSourceId, event.getSourceId())
                .eq(BizPayable::getContractId, event.getContractId()));
        if (exists != null && exists > 0) {
            log.info("结算应付行已存在，跳过生成, sourceType={}, sourceId={}, contractId={}",
                    event.getSourceType(), event.getSourceId(), event.getContractId());
            return null;
        }
        BigDecimal amount = event.getAmount() == null ? BigDecimal.ZERO : event.getAmount();
        if (amount.signum() <= 0) {
            log.info("结算金额非正，不生成应付台账, sourceType={}, sourceId={}, amount={}",
                    event.getSourceType(), event.getSourceId(), amount);
            return null;
        }

        BizPayable payable = new BizPayable();
        payable.setProjectId(event.getProjectId());
        payable.setContractId(event.getContractId());
        payable.setContractCategory(event.getContractCategory());
        payable.setSourceType(event.getSourceType());
        payable.setSourceId(event.getSourceId());
        payable.setPayableAmount(amount);
        payable.setPaidAmount(BigDecimal.ZERO);
        payable.setDueDate(LocalDate.now().plusDays(defaultCreditDays));
        payable.setStatus(BizPayable.STATUS_OPEN);
        payable.setRemark(event.getRemark() != null ? event.getRemark() : "结算审批通过生成");
        payableMapper.insert(payable);

        projectMapper.addPayableAmount(event.getProjectId(), amount);
        log.info("结算生效生成应付台账, sourceType={}, sourceId={}, contractId={}, projectId={}, amount={}, dueDate={}",
                event.getSourceType(), event.getSourceId(), event.getContractId(),
                event.getProjectId(), amount, payable.getDueDate());
        return payable;
    }

    /**
     * 结算删除/作废冲减应付（对称生成；幂等由 GREATEST 截断保证：重复冲减余额不再变化）。
     * <p>项目应付单值按<b>实际余额减少额</b>回减——若冲减额超过当前余额（数据异常），
     * SQL 已截断至 0，按实际额回减并如实告警。</p>
     *
     * @param event 支出结算冲销事件（reversed=true）
     * @return 实际冲减的余额合计
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal reduceOnSettlementReverse(ExpenseSettlementPayableEvent event) {
        validateEvent(event);
        if (!event.isReversed()) {
            throw new BusinessException("冲减接口要求 reversed=true 事件");
        }
        List<BizPayable> rows = payableMapper.selectList(new LambdaQueryWrapper<BizPayable>()
                .eq(BizPayable::getSourceType, event.getSourceType())
                .eq(BizPayable::getSourceId, event.getSourceId())
                .eq(BizPayable::getContractId, event.getContractId()));
        if (rows.isEmpty()) {
            log.info("结算冲减应付：无对应台账行, sourceType={}, sourceId={}, contractId={}",
                    event.getSourceType(), event.getSourceId(), event.getContractId());
            return BigDecimal.ZERO;
        }
        BigDecimal totalBalanceReduced = BigDecimal.ZERO;
        for (BizPayable row : rows) {
            BigDecimal balanceBefore = row.getPayableAmount()
                    .subtract(row.getPaidAmount() != null ? row.getPaidAmount() : BigDecimal.ZERO);
            payableMapper.reducePayableAmount(row.getId(), event.getAmount());
            BigDecimal actualReduced = event.getAmount().min(balanceBefore.max(BigDecimal.ZERO));
            if (actualReduced.signum() > 0) {
                projectMapper.addPayableAmount(row.getProjectId(), actualReduced.negate());
            }
            totalBalanceReduced = totalBalanceReduced.add(actualReduced);
            if (actualReduced.compareTo(event.getAmount()) < 0) {
                log.warn("结算冲减应付超过当前余额，已按余额截断, payableId={}, 冲减额={}, 冲减前余额={}",
                        row.getId(), event.getAmount(), balanceBefore);
            }
        }
        log.info("结算删除冲减应付台账, sourceType={}, sourceId={}, contractId={}, 冲减额={}, 实际余额减少={}",
                event.getSourceType(), event.getSourceId(), event.getContractId(),
                event.getAmount(), totalBalanceReduced);
        return totalBalanceReduced;
    }

    // ==================== 核销 ====================

    /**
     * 付款生效核销应付（按到期日 FIFO 冲减<b>同合同</b> OPEN 余额，逐笔落核销明细）。
     * <p>核销额以该合同 OPEN 余额合计为上限：超出部分不冲减（预付事实由付款单据体现），
     * 返回实际核销金额，不静默丢弃。</p>
     *
     * @param paymentApplyId 付款申请ID
     * @param projectId      项目ID
     * @param contractId     支出合同ID
     * @param paymentAmount  本次生效付款金额（&gt;0）
     * @return 实际核销金额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal writeOffForContract(Long paymentApplyId, Long projectId,
                                          Long contractId, BigDecimal paymentAmount) {
        if (paymentApplyId == null) {
            throw new BusinessException("付款申请ID不能为空");
        }
        if (projectId == null || contractId == null) {
            throw new BusinessException("项目ID与合同ID不能为空");
        }
        if (paymentAmount == null || paymentAmount.signum() <= 0) {
            throw new BusinessException("核销金额必须大于0");
        }
        List<BizPayable> opens = payableMapper.selectList(new LambdaQueryWrapper<BizPayable>()
                .eq(BizPayable::getContractId, contractId)
                .eq(BizPayable::getStatus, BizPayable.STATUS_OPEN)
                .orderByAsc(BizPayable::getDueDate)
                .orderByAsc(BizPayable::getId));

        BigDecimal remaining = paymentAmount;
        BigDecimal writtenOff = BigDecimal.ZERO;
        for (BizPayable p : opens) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal balance = p.getPayableAmount().subtract(
                    p.getPaidAmount() != null ? p.getPaidAmount() : BigDecimal.ZERO);
            if (balance.signum() <= 0) {
                continue;
            }
            BigDecimal offset = balance.min(remaining);
            payableMapper.addPaidAmount(p.getId(), offset);

            BizPayableWriteOff detail = new BizPayableWriteOff();
            detail.setPayableId(p.getId());
            detail.setPaymentApplyId(paymentApplyId);
            detail.setAmount(offset);
            writeOffMapper.insert(detail);

            remaining = remaining.subtract(offset);
            writtenOff = writtenOff.add(offset);
        }
        if (writtenOff.signum() > 0) {
            projectMapper.addPayableAmount(projectId, writtenOff.negate());
        }
        if (remaining.signum() > 0) {
            log.info("付款核销应付：合同 {} 无足额 OPEN 应付，本次核销 {}，超出部分 {} 为预付（不冲减台账）",
                    contractId, writtenOff, remaining);
        }
        return writtenOff;
    }

    /**
     * 反冲某付款申请的全部核销（与 {@link #writeOffForContract} 对称，供付款反冲路径使用）。
     * <p>项目应付单值按实际可反冲额回加（SQL GREATEST 已截断，见应收同款纪律）。</p>
     *
     * @param paymentApplyId 付款申请ID
     * @return 实际反冲金额合计
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal reverseWriteOff(Long paymentApplyId) {
        List<BizPayableWriteOff> details = writeOffMapper.selectList(
                new LambdaQueryWrapper<BizPayableWriteOff>()
                        .eq(BizPayableWriteOff::getPaymentApplyId, paymentApplyId));
        BigDecimal total = BigDecimal.ZERO;
        for (BizPayableWriteOff detail : details) {
            BizPayable p = payableMapper.selectById(detail.getPayableId());
            if (p == null) {
                log.warn("反冲核销：应付台账行不存在, payableId={}, 跳过该笔明细", detail.getPayableId());
                writeOffMapper.deleteById(detail.getId());
                continue;
            }
            BigDecimal offAmount = detail.getAmount() != null ? detail.getAmount() : BigDecimal.ZERO;
            BigDecimal paid = p.getPaidAmount() != null ? p.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal actualOffset = offAmount.min(paid);
            if (actualOffset.compareTo(offAmount) < 0) {
                log.warn("反冲核销额超过当前已核销额，按实际可反冲额处理, payableId={}, offAmount={}, paid={}",
                        p.getId(), offAmount, paid);
            }
            payableMapper.addPaidAmount(p.getId(), offAmount.negate());
            projectMapper.addPayableAmount(p.getProjectId(), actualOffset);
            writeOffMapper.deleteById(detail.getId());
            total = total.add(actualOffset);
        }
        if (total.signum() > 0) {
            log.info("付款反冲应付核销, paymentApplyId={}, 反冲合计={}", paymentApplyId, total);
        }
        return total;
    }

    // ==================== 查询 ====================

    /**
     * 分页查询应付台账（projectId/status/contractCategory 可选筛选，按到期日升序）
     */
    public PageResult<BizPayable> page(int page, int size, Long projectId, String status, String contractCategory) {
        return page(page, size, projectId, status, contractCategory, null);
    }

    /**
     * 分页查询应付台账（增加 contractId 精确筛选，供 L4 应付断言/合同级对账使用）。
     */
    public PageResult<BizPayable> page(int page, int size, Long projectId, String status,
                                       String contractCategory, Long contractId) {
        Page<BizPayable> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizPayable> wrapper = new LambdaQueryWrapper<BizPayable>()
                .eq(projectId != null, BizPayable::getProjectId, projectId)
                .eq(status != null && !status.isBlank(), BizPayable::getStatus, status)
                .eq(contractCategory != null && !contractCategory.isBlank(),
                        BizPayable::getContractCategory, contractCategory)
                .eq(contractId != null, BizPayable::getContractId, contractId)
                .orderByAsc(BizPayable::getDueDate)
                // 稳定次序：同日到期时按 id 升序，保证分页/L4 断言取行确定
                .orderByAsc(BizPayable::getId);
        Page<BizPayable> result = payableMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizPayable::getProjectId, BizPayable::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 应付账龄分析（按项目 × 账龄桶汇总 OPEN 余额，口径同应收；项目按逾期余额降序）。
     */
    public Map<String, Object> aging(Long projectId) {
        List<BizPayable> opens = payableMapper.selectList(new LambdaQueryWrapper<BizPayable>()
                .eq(projectId != null, BizPayable::getProjectId, projectId)
                .eq(BizPayable::getStatus, BizPayable.STATUS_OPEN));
        LocalDate today = LocalDate.now();

        Map<Long, Map<String, Object>> byProject = new LinkedHashMap<>();
        BigDecimal totalOpen = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        for (BizPayable p : opens) {
            BigDecimal balance = p.getPayableAmount().subtract(
                    p.getPaidAmount() != null ? p.getPaidAmount() : BigDecimal.ZERO);
            if (balance.signum() <= 0) {
                continue;
            }
            long overdueDays = ChronoUnit.DAYS.between(p.getDueDate(), today);
            String bucket = bucketOf(overdueDays);
            boolean overdue = overdueDays > 0;

            Map<String, Object> row = byProject.computeIfAbsent(p.getProjectId(), k -> {
                Map<String, Object> init = new HashMap<>();
                init.put("projectId", k);
                init.put("openBalance", BigDecimal.ZERO);
                init.put("overdueBalance", BigDecimal.ZERO);
                init.put("maxOverdueDays", 0L);
                Map<String, BigDecimal> buckets = new LinkedHashMap<>();
                for (String b : AGING_BUCKETS) {
                    buckets.put(b, BigDecimal.ZERO);
                }
                init.put("buckets", buckets);
                return init;
            });
            row.put("openBalance", ((BigDecimal) row.get("openBalance")).add(balance));
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> buckets = (Map<String, BigDecimal>) row.get("buckets");
            buckets.merge(bucket, balance, BigDecimal::add);
            if (overdue) {
                row.put("overdueBalance", ((BigDecimal) row.get("overdueBalance")).add(balance));
                row.put("maxOverdueDays", Math.max((Long) row.get("maxOverdueDays"), overdueDays));
                totalOverdue = totalOverdue.add(balance);
            }
            totalOpen = totalOpen.add(balance);
        }

        List<Map<String, Object>> projects = new ArrayList<>(byProject.values());
        for (Map<String, Object> row : projects) {
            var project = projectMapper.selectById((Long) row.get("projectId"));
            row.put("projectName", project != null ? project.getProjectName() : null);
        }
        projects.sort((a, b) -> ((BigDecimal) b.get("overdueBalance")).compareTo((BigDecimal) a.get("overdueBalance")));

        Map<String, Object> result = new HashMap<>();
        result.put("totalOpen", totalOpen);
        result.put("totalOverdue", totalOverdue);
        result.put("projects", projects);
        return result;
    }

    // ==================== 私有方法 ====================

    private void validateEvent(ExpenseSettlementPayableEvent event) {
        if (event == null || event.getSourceType() == null || event.getSourceId() == null
                || event.getProjectId() == null || event.getContractId() == null
                || event.getContractCategory() == null) {
            throw new BusinessException("应付台账事件负载不完整（sourceType/sourceId/projectId/contractId/contractCategory 必填）");
        }
    }

    private String bucketOf(long overdueDays) {
        if (overdueDays <= 0) {
            return "NOT_DUE";
        }
        if (overdueDays <= 30) {
            return "D0_30";
        }
        if (overdueDays <= 60) {
            return "D31_60";
        }
        if (overdueDays <= 90) {
            return "D61_90";
        }
        return "OVER_90";
    }
}
