package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizProjectSettlement;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.domain.BizReceivableWriteOff;
import com.zwinsight.finance.mapper.BizReceivableMapper;
import com.zwinsight.finance.mapper.BizReceivableWriteOffMapper;
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
 * 应收台账服务（V2026_57）
 * <p>
 * 生成：项目结算审批通过时按「最终结算金额（空则累计产值）− 累计收款」的正差额生成应收记录；
 * 核销：回款登记生效时按到期日 FIFO 冲减 OPEN 余额；
 * 双写：每次生成/核销同事务原子增减 biz_project.receivable_amount，
 * 维护不变量「项目应收单值 = 台账 OPEN 余额合计」。
 * </p>
 * <p>账龄口径（驾驶舱 V1 §10 回款风险）：NOT_DUE（未到期）/ 0-30 / 31-60 / 61-90 / OVER_90，
 * 逾期天数 = 今天 − 到期日。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceivableService {

    private final BizReceivableMapper receivableMapper;
    private final BizReceivableWriteOffMapper writeOffMapper;
    private final BizProjectMapper projectMapper;

    /** 默认账期天数（结算审批日 + N 天为应收到期日；合同未约定收款日时使用，可配置不硬编码） */
    @Value("${zw.finance.receivable.default-credit-days:30}")
    private int defaultCreditDays;

    /** 账龄桶定义（顺序即展示顺序） */
    private static final String[] AGING_BUCKETS = {"NOT_DUE", "D0_30", "D31_60", "D61_90", "OVER_90"};

    // ==================== 生成 ====================

    /**
     * 结算审批通过生成应收记录（幂等：同 source 已存在则跳过）。
     * <p>应收金额 = 最终结算金额（空则累计产值）− 累计收款；差额 ≤ 0 不生成
     * （已收足/超收不伪造应收，超收事实由回款单据体现）。</p>
     *
     * @param settlement 已审批的项目结算单
     * @return 生成的应收记录；差额≤0 或已存在时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public BizReceivable generateFromSettlement(BizProjectSettlement settlement) {
        if (settlement == null || settlement.getId() == null) {
            throw new BusinessException("结算单不存在");
        }
        Long exists = receivableMapper.selectCount(new LambdaQueryWrapper<BizReceivable>()
                .eq(BizReceivable::getSourceType, BizReceivable.SOURCE_SETTLEMENT)
                .eq(BizReceivable::getSourceId, settlement.getId()));
        if (exists != null && exists > 0) {
            log.info("结算单应收记录已存在，跳过生成, settlementId={}", settlement.getId());
            return null;
        }

        BigDecimal basis = settlement.getFinalSettlementAmount() != null
                ? settlement.getFinalSettlementAmount()
                : (settlement.getCumulativeOutput() != null ? settlement.getCumulativeOutput() : BigDecimal.ZERO);
        BigDecimal received = settlement.getCumulativeReceived() != null
                ? settlement.getCumulativeReceived() : BigDecimal.ZERO;
        BigDecimal amount = basis.subtract(received);
        if (amount.signum() <= 0) {
            log.info("结算单无应收差额，不生成台账, settlementId={}, basis={}, received={}",
                    settlement.getId(), basis, received);
            return null;
        }

        BizReceivable receivable = new BizReceivable();
        receivable.setProjectId(settlement.getProjectId());
        receivable.setSourceType(BizReceivable.SOURCE_SETTLEMENT);
        receivable.setSourceId(settlement.getId());
        receivable.setReceivableAmount(amount);
        receivable.setDueDate(LocalDate.now().plusDays(defaultCreditDays));
        receivable.setWrittenOffAmount(BigDecimal.ZERO);
        receivable.setStatus(BizReceivable.STATUS_OPEN);
        receivable.setRemark("结算单 " + settlement.getSettlementCode() + " 审批通过生成");
        receivableMapper.insert(receivable);

        // 双写项目应收单值（不变量：单值 = OPEN 余额合计）
        projectMapper.addReceivableAmount(settlement.getProjectId(), amount);
        log.info("结算审批生成应收台账, settlementId={}, projectId={}, amount={}, dueDate={}",
                settlement.getId(), settlement.getProjectId(), amount, receivable.getDueDate());
        return receivable;
    }

    // ==================== 核销 ====================

    /**
     * 回款生效核销应收（按到期日 FIFO 冲减 OPEN 余额，逐笔落核销明细）。
     * <p>核销额以 OPEN 余额合计为上限：超出部分不冲减（预收/超收事实由回款单据体现），
     * 返回实际核销金额并记录日志，不静默丢弃。</p>
     *
     * @param paymentReceivedId 回款登记ID
     * @param projectId         项目ID
     * @param receiveAmount     本次回款金额（&gt;0）
     * @return 实际核销金额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal writeOff(Long paymentReceivedId, Long projectId, BigDecimal receiveAmount) {
        if (paymentReceivedId == null) {
            throw new BusinessException("回款登记ID不能为空");
        }
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (receiveAmount == null || receiveAmount.signum() <= 0) {
            throw new BusinessException("核销金额必须大于0");
        }
        List<BizReceivable> opens = receivableMapper.selectList(new LambdaQueryWrapper<BizReceivable>()
                .eq(BizReceivable::getProjectId, projectId)
                .eq(BizReceivable::getStatus, BizReceivable.STATUS_OPEN)
                .orderByAsc(BizReceivable::getDueDate)
                .orderByAsc(BizReceivable::getId));

        BigDecimal remaining = receiveAmount;
        BigDecimal writtenOff = BigDecimal.ZERO;
        for (BizReceivable r : opens) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal balance = r.getReceivableAmount().subtract(
                    r.getWrittenOffAmount() != null ? r.getWrittenOffAmount() : BigDecimal.ZERO);
            if (balance.signum() <= 0) {
                continue;
            }
            BigDecimal offset = balance.min(remaining);
            // 原子累加 + 状态切换（避免 read-modify-write 丢失更新，见 Mapper 类注释）
            receivableMapper.addWrittenOffAmount(r.getId(), offset);

            // 核销明细落库（回款改额/删除时按此精确反冲）
            BizReceivableWriteOff detail = new BizReceivableWriteOff();
            detail.setReceivableId(r.getId());
            detail.setPaymentReceivedId(paymentReceivedId);
            detail.setAmount(offset);
            writeOffMapper.insert(detail);

            remaining = remaining.subtract(offset);
            writtenOff = writtenOff.add(offset);
        }
        if (writtenOff.signum() > 0) {
            projectMapper.addReceivableAmount(projectId, writtenOff.negate());
        }
        if (remaining.signum() > 0) {
            log.info("回款核销应收：项目 {} 无足额 OPEN 应收，本次核销 {}，超出部分 {} 为预收/超收（不冲减台账）",
                    projectId, writtenOff, remaining);
        }
        return writtenOff;
    }

    /**
     * 反冲某回款登记的全部核销（回款改额/删除时调用，与 writeOff 对称）。
     * <p>按核销明细逐笔回退：台账 written_off_amount 原子冲减（SQL 层 GREATEST 防负）、
     * 状态按结果自动回 OPEN，项目 receivable_amount 同事务回加；明细逻辑删除保留审计痕迹。</p>
     * <p><b>不变量纪律</b>：项目单值回加的是「实际可反冲额」而非明细金额——
     * 若明细金额 > 当前已核销额（并发或人工改库造成数据异常），SQL 已截断至 0，
     * 此时多回的金额会让单值虚高于台账 OPEN 合计，故按实际值回加并如实告警（不静默）。</p>
     *
     * @param paymentReceivedId 回款登记ID
     * @return 实际反冲金额合计（用于维护项目应收单值）
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal reverseWriteOff(Long paymentReceivedId) {
        List<BizReceivableWriteOff> details = writeOffMapper.selectList(
                new LambdaQueryWrapper<BizReceivableWriteOff>()
                        .eq(BizReceivableWriteOff::getPaymentReceivedId, paymentReceivedId));
        BigDecimal total = BigDecimal.ZERO;
        for (BizReceivableWriteOff detail : details) {
            BizReceivable r = receivableMapper.selectById(detail.getReceivableId());
            if (r == null) {
                log.warn("反冲核销：应收台账记录不存在, receivableId={}, 跳过该笔明细", detail.getReceivableId());
                writeOffMapper.deleteById(detail.getId());
                continue;
            }
            BigDecimal offAmount = detail.getAmount() != null ? detail.getAmount() : BigDecimal.ZERO;
            BigDecimal written = r.getWrittenOffAmount() != null ? r.getWrittenOffAmount() : BigDecimal.ZERO;
            BigDecimal actualOffset = offAmount;
            if (offAmount.compareTo(written) > 0) {
                // 数据异常（并发或人工改库）：SQL 层 GREATEST 已截断，项目单值须按实际可反冲额回加
                log.warn("反冲核销额超过当前已核销额，按实际可反冲额处理, receivableId={}, offAmount={}, currentWrittenOff={}",
                        r.getId(), offAmount, written);
                actualOffset = written;
            }
            receivableMapper.addWrittenOffAmount(r.getId(), offAmount.negate());
            projectMapper.addReceivableAmount(r.getProjectId(), actualOffset);
            writeOffMapper.deleteById(detail.getId());
            total = total.add(actualOffset);
        }
        if (total.signum() > 0) {
            log.info("回款反冲应收核销, paymentReceivedId={}, 反冲合计={}", paymentReceivedId, total);
        }
        return total;
    }

    // ==================== 查询 ====================

    /**
     * 分页查询应收台账（projectId/status 可选筛选，按到期日升序——最紧急在前）
     */
    public PageResult<BizReceivable> page(int page, int size, Long projectId, String status) {
        Page<BizReceivable> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizReceivable> wrapper = new LambdaQueryWrapper<BizReceivable>()
                .eq(projectId != null, BizReceivable::getProjectId, projectId)
                .eq(status != null && !status.isBlank(), BizReceivable::getStatus, status)
                .orderByAsc(BizReceivable::getDueDate);
        Page<BizReceivable> result = receivableMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizReceivable::getProjectId, BizReceivable::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 应收账龄分析（按项目 × 账龄桶汇总 OPEN 余额）。
     * <p>返回：{ totalOpen, totalOverdue, projects: [{ projectId, projectName, openBalance,
     * overdueBalance, maxOverdueDays, buckets: { NOT_DUE, D0_30, D31_60, D61_90, OVER_90 } }] }，
     * 项目按逾期余额降序（风险大的在前，对齐驾驶舱"异常冒出来"原则）。</p>
     *
     * @param projectId 项目ID（可选，空则全部项目）
     */
    public Map<String, Object> aging(Long projectId) {
        List<BizReceivable> opens = receivableMapper.selectList(new LambdaQueryWrapper<BizReceivable>()
                .eq(projectId != null, BizReceivable::getProjectId, projectId)
                .eq(BizReceivable::getStatus, BizReceivable.STATUS_OPEN));
        LocalDate today = LocalDate.now();

        Map<Long, Map<String, Object>> byProject = new LinkedHashMap<>();
        BigDecimal totalOpen = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        for (BizReceivable r : opens) {
            BigDecimal balance = r.getReceivableAmount().subtract(
                    r.getWrittenOffAmount() != null ? r.getWrittenOffAmount() : BigDecimal.ZERO);
            if (balance.signum() <= 0) {
                continue;
            }
            long overdueDays = ChronoUnit.DAYS.between(r.getDueDate(), today);
            String bucket = bucketOf(overdueDays);
            boolean overdue = overdueDays > 0;

            Map<String, Object> row = byProject.computeIfAbsent(r.getProjectId(), k -> {
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
        // 填充项目名并按逾期余额降序
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

    /**
     * OPEN 应收按到期日落月汇总（滚动预测收款侧数据源；V2026_57）。
     *
     * @param projectId 项目ID（可选，空则公司整体）
     * @return {yyyy-MM: 余额合计}（仅含到期日在今天之后的未来月份）
     */
    public Map<String, BigDecimal> openBalanceByDueMonth(Long projectId) {
        List<BizReceivable> opens = receivableMapper.selectList(new LambdaQueryWrapper<BizReceivable>()
                .eq(projectId != null, BizReceivable::getProjectId, projectId)
                .eq(BizReceivable::getStatus, BizReceivable.STATUS_OPEN));
        Map<String, BigDecimal> byMonth = new HashMap<>();
        for (BizReceivable r : opens) {
            BigDecimal balance = r.getReceivableAmount().subtract(
                    r.getWrittenOffAmount() != null ? r.getWrittenOffAmount() : BigDecimal.ZERO);
            if (balance.signum() <= 0 || r.getDueDate() == null) {
                continue;
            }
            byMonth.merge(String.format("%d-%02d", r.getDueDate().getYear(), r.getDueDate().getMonthValue()),
                    balance, BigDecimal::add);
        }
        return byMonth;
    }

    // ==================== 私有方法 ====================

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
