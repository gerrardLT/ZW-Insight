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

    // ==================== §10 下钻 8 级链（V2026_69）====================

    /**
     * 维护应收下钻信息（§10 第 3/5/6/7/8 级的人工登记项）。
     * <p>为何需要人工登记：<b>工程节点无自动回填数据源</b>——结算单
     * {@code biz_project_settlement} 无任何节点/期次字段；产值报告 {@code biz_output_report}
     * 虽有 report_period，但与结算单之间无外键或单号关联，按 project_id + 时间近似匹配
     * 会制造假关联，属伪造数据，故不采用。甲方审核状态/负责人/下一步动作属甲方侧与
     * 内部管理信息，系统内同样无来源单据。</p>
     * <p>语义：字符串字段 null=不修改、空串=清空；日期字段 null=不修改且不支持清空
     * （见 {@link com.zwinsight.finance.dto.ReceivableDrillInfoRequest} 类注释）。
     * 甲方审核状态非法值报 400，<b>不静默当作未登记</b>（否则录错字会静默丢数据）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public BizReceivable updateDrillInfo(Long id,
                                        com.zwinsight.finance.dto.ReceivableDrillInfoRequest request) {
        if (id == null) {
            throw new BusinessException(400, "应收台账记录ID不能为空");
        }
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        BizReceivable receivable = receivableMapper.selectById(id);
        if (receivable == null) {
            throw new BusinessException(404, "应收台账记录不存在：" + id);
        }

        if (request.getOwnerReviewStatus() != null) {
            String status = request.getOwnerReviewStatus().trim();
            if (status.isEmpty()) {
                receivable.setOwnerReviewStatus(null);
                // 状态撤回时审核日期一并清空，否则残留孤立日期（无状态的审核日无意义）
                receivable.setOwnerReviewDate(null);
            } else {
                String upper = status.toUpperCase(java.util.Locale.ROOT);
                if (!BizReceivable.REVIEW_STATUSES.contains(upper)) {
                    throw new BusinessException(400,
                            "甲方审核状态不合法，可选值：" + BizReceivable.REVIEW_STATUSES);
                }
                receivable.setOwnerReviewStatus(upper);
            }
        }
        if (request.getMilestoneNode() != null) {
            receivable.setMilestoneNode(blankToNull(request.getMilestoneNode()));
        }
        if (request.getNextAction() != null) {
            receivable.setNextAction(blankToNull(request.getNextAction()));
        }
        if (request.getOwnerName() != null) {
            String name = blankToNull(request.getOwnerName());
            receivable.setOwnerName(name);
            if (name == null) {
                // 姓名清空时 ID 一并撤回，避免残留指向不明用户的 ownerId
                receivable.setOwnerId(null);
            }
        }
        if (request.getOwnerId() != null) {
            receivable.setOwnerId(request.getOwnerId());
        }
        if (request.getApplyDate() != null) {
            receivable.setApplyDate(request.getApplyDate());
        }
        if (request.getOwnerReviewDate() != null) {
            receivable.setOwnerReviewDate(request.getOwnerReviewDate());
        }

        receivableMapper.updateById(receivable);
        log.info("应收下钻信息已更新, id={}, reviewStatus={}, owner={}, milestone={}",
                id, receivable.getOwnerReviewStatus(), receivable.getOwnerName(),
                receivable.getMilestoneNode());
        return receivable;
    }

    /**
     * §10 下钻 8 级链：项目 → 应收款 → 对应工程节点 → 应收日期 → 实际申请日期
     * → 甲方审核状态 → 负责人 → 下一步动作。
     * <p>每级带 {@code registered} 标记：false 即「未登记」，前端据此显示“未登记”并提供编辑入口，
     * <b>不用默认值冒充已登记</b>。另附派生信息：未结清余额、逾期天数、账龄分档、
     * 甲方停留天数（申请日→审核日，未审核时算至今）。</p>
     */
    public Map<String, Object> getDrillChain(Long id) {
        if (id == null) {
            throw new BusinessException(400, "应收台账记录ID不能为空");
        }
        BizReceivable r = receivableMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(404, "应收台账记录不存在：" + id);
        }
        // projectName 为非持久化展示字段，需单独取（不伪造为空）
        String projectName = null;
        if (r.getProjectId() != null) {
            com.zwinsight.project.domain.BizProject project = projectMapper.selectById(r.getProjectId());
            projectName = project != null ? project.getProjectName() : null;
        }

        BigDecimal amount = r.getReceivableAmount() == null ? BigDecimal.ZERO : r.getReceivableAmount();
        BigDecimal writtenOff = r.getWrittenOffAmount() == null ? BigDecimal.ZERO : r.getWrittenOffAmount();
        // 未结清余额下限 0：超收事实由回款单据体现，不在台账上出负数
        BigDecimal openBalance = amount.subtract(writtenOff).max(BigDecimal.ZERO);

        List<Map<String, Object>> chain = new ArrayList<>();
        chain.add(drillLevel(1, "项目", projectName, projectName != null, r.getProjectId()));
        chain.add(drillLevel(2, "应收款", openBalance, true, r.getId()));
        chain.add(drillLevel(3, "对应工程节点", r.getMilestoneNode(), r.getMilestoneNode() != null, null));
        chain.add(drillLevel(4, "应收日期", r.getDueDate(), r.getDueDate() != null, null));
        chain.add(drillLevel(5, "实际申请日期", r.getApplyDate(), r.getApplyDate() != null, null));
        chain.add(drillLevel(6, "甲方审核状态", reviewStatusLabel(r.getOwnerReviewStatus()),
                r.getOwnerReviewStatus() != null, null));
        chain.add(drillLevel(7, "负责人", r.getOwnerName(), r.getOwnerName() != null, r.getOwnerId()));
        chain.add(drillLevel(8, "下一步动作", r.getNextAction(), r.getNextAction() != null, null));

        // 派生信息（均为真实计算，无估算）
        long overdueDays = 0;
        boolean overdue = false;
        if (r.getDueDate() != null && BizReceivable.STATUS_OPEN.equals(r.getStatus())
                && r.getDueDate().isBefore(LocalDate.now())) {
            overdueDays = ChronoUnit.DAYS.between(r.getDueDate(), LocalDate.now());
            overdue = true;
        }
        Long ownerStayDays = null;
        if (r.getApplyDate() != null) {
            LocalDate end = r.getOwnerReviewDate() != null ? r.getOwnerReviewDate() : LocalDate.now();
            // 审核日早于申请日属录入错误，如实给负值而不是归零掩盖
            ownerStayDays = ChronoUnit.DAYS.between(r.getApplyDate(), end);
        }
        long unregisteredCount = chain.stream().filter(l -> !Boolean.TRUE.equals(l.get("registered"))).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("receivableId", r.getId());
        result.put("projectId", r.getProjectId());
        result.put("projectName", projectName);
        result.put("sourceType", r.getSourceType());
        result.put("sourceId", r.getSourceId());
        result.put("status", r.getStatus());
        result.put("receivableAmount", amount);
        result.put("writtenOffAmount", writtenOff);
        result.put("openBalance", openBalance);
        result.put("overdue", overdue);
        result.put("overdueDays", overdueDays);
        result.put("agingBucket", overdue ? bucketOf(overdueDays) : null);
        result.put("ownerStayDays", ownerStayDays);
        result.put("chain", chain);
        // 原始登记值（chain 里的甲方审核状态已转中文标签，前端编辑表单回填需要原码）
        Map<String, Object> drillInfo = new LinkedHashMap<>();
        drillInfo.put("milestoneNode", r.getMilestoneNode());
        drillInfo.put("applyDate", r.getApplyDate());
        drillInfo.put("ownerReviewStatus", r.getOwnerReviewStatus());
        drillInfo.put("ownerReviewDate", r.getOwnerReviewDate());
        drillInfo.put("ownerId", r.getOwnerId());
        drillInfo.put("ownerName", r.getOwnerName());
        drillInfo.put("nextAction", r.getNextAction());
        result.put("drillInfo", drillInfo);
        result.put("unregisteredCount", unregisteredCount);
        // 下钻链未登记的级数不为 0 时，前端应引导补登（而不是把空链当成正常）
        result.put("complete", unregisteredCount == 0);
        return result;
    }

    private Map<String, Object> drillLevel(int level, String label, Object value,
                                           boolean registered, Object refId) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("level", level);
        node.put("label", label);
        node.put("value", value);
        node.put("registered", registered);
        node.put("refId", refId);
        return node;
    }

    /** 甲方审核状态中文标签；NULL 返回 null（由前端显示「未登记」，不返回“未知”之类默认词） */
    private String reviewStatusLabel(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case BizReceivable.REVIEW_SUBMITTED -> "已提交甲方";
            case BizReceivable.REVIEW_UNDER_REVIEW -> "甲方审核中";
            case BizReceivable.REVIEW_CONFIRMED -> "甲方已确认";
            case BizReceivable.REVIEW_DISPUTED -> "甲方有异议";
            default -> status;
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
