package com.zwinsight.dashboard.risk.rule;

import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.finance.mapper.BizEntertainmentDetailMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 招待费异常风险规则（资金流转文档 §11 招待费预警项，共 8 类）：
 * <pre>
 * 单笔超限额 / 无事由 / 无招待对象 / 发票不完整   → RED（合规硬伤）
 * 无事前审批 / 同人同日多笔 / 同人单月高频 / 超月度限额 → YELLOW（管理偏差）
 * </pre>
 * 数据源：biz_reimbursement_detail(category_code=EXP-INDIRECT-ENTERTAIN)
 * JOIN biz_entertainment_detail，且仅统计已生效（APPROVED）报销单。
 * 粒度 = 项目（仅扫描确有招待费记录的项目，避免全项目 N+1）。
 */
@Component
@RequiredArgsConstructor
public class EntertainmentAnomalyRule implements RiskRule {

    public static final String TYPE = "ENTERTAINMENT_ANOMALY";

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final BizEntertainmentDetailMapper entertainmentMapper;
    private final BizProjectMapper projectMapper;

    /** 单笔招待费限额（默认 3000 元，文档 §8.1「单笔 >3000 元」示例口径） */
    @Value("${zw.risk.entertainment-single-limit:3000}")
    private BigDecimal singleLimit;

    /** 同一经办人单月招待笔数上限（默认 5 次） */
    @Value("${zw.risk.entertainment-handler-monthly-count:5}")
    private int handlerMonthlyCountLimit;

    @Override
    public String riskType() {
        return TYPE;
    }

    @Override
    public List<RiskFinding> evaluate() {
        List<Long> projectIds = entertainmentMapper.listProjectIdsWithEntertainment();
        List<RiskFinding> findings = new ArrayList<>();
        String currentMonth = YearMonth.now().format(MONTH_FMT);

        for (Long projectId : projectIds) {
            Map<String, Object> summary = entertainmentMapper.analyzeEntertainment(projectId, null);
            if (summary == null) {
                continue;
            }
            BigDecimal totalAmount = toDecimal(summary.get("totalAmount"));
            BigDecimal maxSingle = toDecimal(summary.get("maxSingleAmount"));
            long totalCount = toLong(summary.get("totalCount"));
            long noReason = toLong(summary.get("noReasonCount"));
            long noHost = toLong(summary.get("noHostCount"));
            long noPreApproval = toLong(summary.get("noPreApprovalCount"));
            long noInvoice = toLong(summary.get("noInvoiceCount"));
            Long sameDayMulti = entertainmentMapper.countSameHandlerSameDay(projectId);
            Long frequentHandler = entertainmentMapper.countFrequentHandlerInMonth(
                    projectId, currentMonth, handlerMonthlyCountLimit);

            // RED 判定：单笔超限 / 无事由 / 无对象 / 发票缺失（票据不完整同属合规硬伤）
            List<String> redHits = new ArrayList<>();
            if (maxSingle.compareTo(singleLimit) > 0) {
                redHits.add("单笔最高 " + maxSingle + " 元超限额 " + singleLimit);
            }
            if (noReason > 0) {
                redHits.add("无事由 " + noReason + " 笔");
            }
            if (noHost > 0) {
                redHits.add("无招待对象 " + noHost + " 笔");
            }
            if (noInvoice > 0) {
                redHits.add("发票不完整 " + noInvoice + " 笔");
            }
            // YELLOW 判定：无事前审批 / 同人同日多笔 / 同人单月高频
            List<String> yellowHits = new ArrayList<>();
            if (noPreApproval > 0) {
                yellowHits.add("无事前审批 " + noPreApproval + " 笔");
            }
            if (sameDayMulti != null && sameDayMulti > 0) {
                yellowHits.add("同人同日多笔 " + sameDayMulti + " 笔");
            }
            if (frequentHandler != null && frequentHandler > 0) {
                yellowHits.add(currentMonth + " 同人超 " + handlerMonthlyCountLimit
                        + " 次的经办人 " + frequentHandler + " 人");
            }
            // §11 第 8 类：超月度限额（旧实现缺失，仅 7/8 类）。
            // 分母取月度资金计划科目明细的招待费计划额；<b>无计划基准时不判定</b>，
            // 否则会把“未编计划”当成“限额 0 元”而对所有支出误报。
            YearMonth ym = YearMonth.now();
            BigDecimal planLimit = entertainmentMapper.sumEntertainmentPlanLimit(
                    projectId, ym.getYear(), ym.getMonthValue());
            if (planLimit != null) {
                BigDecimal monthActual = BigDecimal.ZERO;
                List<Map<String, Object>> trend = entertainmentMapper.monthlyTrend(projectId, ym.atDay(1));
                if (trend != null) {
                    for (Map<String, Object> row : trend) {
                        if (currentMonth.equals(String.valueOf(row.get("month")))) {
                            monthActual = toDecimal(row.get("amount"));
                            break;
                        }
                    }
                }
                if (monthActual.compareTo(planLimit) > 0) {
                    yellowHits.add(currentMonth + " 招待费 " + monthActual + " 元超计划限额 " + planLimit + " 元");
                }
            }

            if (redHits.isEmpty() && yellowHits.isEmpty()) {
                continue; // 全部正常，不产生风险（正常费用隐藏）
            }
            String severity = redHits.isEmpty()
                    ? BizRiskRegister.SEVERITY_YELLOW : BizRiskRegister.SEVERITY_RED;
            BizProject project = projectMapper.selectById(projectId);
            String projectName = project != null ? project.getProjectName() : String.valueOf(projectId);
            List<String> allHits = new ArrayList<>(redHits);
            allHits.addAll(yellowHits);
            String title = String.format("项目【%s】招待费异常（累计 %s 元 / %d 次）：%s",
                    projectName, totalAmount, totalCount, String.join("；", allHits));
            String reason = String.format(
                    "{\"totalAmount\":%s,\"totalCount\":%d,\"maxSingleAmount\":%s,\"noReasonCount\":%d,"
                            + "\"noHostCount\":%d,\"noPreApprovalCount\":%d,\"noInvoiceCount\":%d,"
                            + "\"sameDayMultiCount\":%d,\"frequentHandlerCount\":%d}",
                    totalAmount, totalCount, maxSingle, noReason, noHost, noPreApproval, noInvoice,
                    sameDayMulti != null ? sameDayMulti : 0,
                    frequentHandler != null ? frequentHandler : 0);
            String ruleParams = String.format("{\"singleLimit\":%s,\"handlerMonthlyCountLimit\":%d}",
                    singleLimit, handlerMonthlyCountLimit);
            findings.add(new RiskFinding(TYPE, projectId, severity, title,
                    totalAmount, reason,
                    "核查招待费凭证与事前审批，补齐事由/对象/发票，高频经办人需专项说明",
                    "PROJECT", projectId, ruleParams));
        }
        return findings;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
