package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizBankAccount;
import com.zwinsight.finance.domain.BizBankFlow;
import com.zwinsight.finance.domain.BizDailyCashReport;
import com.zwinsight.finance.mapper.BizBankAccountMapper;
import com.zwinsight.finance.mapper.BizBankFlowMapper;
import com.zwinsight.finance.mapper.BizDailyCashReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 资金日报快照服务（每日头寸，老板视角）
 * <p>数据源：账户余额来自 {@link BankFlowService#latestBalances}，当日收支来自 biz_bank_flow；
 * 大额支出门槛默认 50 万（可由调用方覆盖）。生成后落 biz_daily_cash_report 快照，
 * 同日重复生成执行覆盖更新（唯一键 report_date）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyCashReportService {

    private final BizDailyCashReportMapper reportMapper;
    private final BizBankAccountMapper accountMapper;
    private final BizBankFlowMapper flowMapper;
    private final BankFlowService bankFlowService;

    /** 大额支出默认门槛：50 万 */
    private static final BigDecimal DEFAULT_LARGE_THRESHOLD = new BigDecimal("500000");

    /**
     * 生成/刷新某日资金日报（幂等：同 report_date 覆盖更新）
     */
    @Transactional(rollbackFor = Exception.class)
    public BizDailyCashReport generate(LocalDate reportDate, BigDecimal largeThreshold) {
        LocalDate date = reportDate != null ? reportDate : LocalDate.now();
        BigDecimal threshold = largeThreshold != null ? largeThreshold : DEFAULT_LARGE_THRESHOLD;

        // 1. 头寸：各账户截至当日的最新余额
        List<Map<String, Object>> balances = bankFlowService.latestBalances(date);
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal basicBalance = BigDecimal.ZERO;
        BigDecimal generalBalance = BigDecimal.ZERO;
        BigDecimal specialBalance = BigDecimal.ZERO;
        int accountCount = 0;
        for (Map<String, Object> row : balances) {
            BigDecimal bal = (BigDecimal) row.get("balance");
            if (bal == null) {
                bal = BigDecimal.ZERO;
            }
            totalBalance = totalBalance.add(bal);
            accountCount++;
            String type = (String) row.get("accountType");
            if ("BASIC".equals(type)) {
                basicBalance = basicBalance.add(bal);
            } else if ("GENERAL".equals(type)) {
                generalBalance = generalBalance.add(bal);
            } else if ("SPECIAL".equals(type)) {
                specialBalance = specialBalance.add(bal);
            }
        }

        // 2. 当日收支：流水按方向汇总
        List<BizBankFlow> dayFlows = flowMapper.selectList(new LambdaQueryWrapper<BizBankFlow>()
                .eq(BizBankFlow::getFlowDate, date));
        BigDecimal inflow = BigDecimal.ZERO;
        BigDecimal outflow = BigDecimal.ZERO;
        int largeCount = 0;
        BigDecimal largeAmount = BigDecimal.ZERO;
        for (BizBankFlow flow : dayFlows) {
            if (BizBankFlow.DIRECTION_IN.equals(flow.getDirection())) {
                inflow = inflow.add(flow.getAmount());
            } else if (BizBankFlow.DIRECTION_OUT.equals(flow.getDirection())) {
                outflow = outflow.add(flow.getAmount());
                if (flow.getAmount().compareTo(threshold) >= 0) {
                    largeCount++;
                    largeAmount = largeAmount.add(flow.getAmount());
                }
            }
        }

        // 3. upsert 快照
        BizDailyCashReport report = reportMapper.selectOne(new LambdaQueryWrapper<BizDailyCashReport>()
                .eq(BizDailyCashReport::getReportDate, date));
        boolean isNew = report == null;
        if (isNew) {
            report = new BizDailyCashReport();
            report.setReportDate(date);
        }
        report.setTotalBalance(totalBalance);
        report.setBasicBalance(basicBalance);
        report.setGeneralBalance(generalBalance);
        report.setSpecialBalance(specialBalance);
        report.setInflowAmount(inflow);
        report.setOutflowAmount(outflow);
        report.setNetPosition(inflow.subtract(outflow));
        report.setLargeOutflowCount(largeCount);
        report.setLargeOutflowAmount(largeAmount);
        report.setAccountCount(accountCount);

        if (isNew) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }
        log.info("资金日报生成完成: date={}, totalBalance={}, inflow={}, outflow={}",
                date, totalBalance, inflow, outflow);
        return report;
    }

    /**
     * 查询某日日报（不传日期返回最近一天）
     */
    public BizDailyCashReport getReport(LocalDate reportDate) {
        LambdaQueryWrapper<BizDailyCashReport> wrapper = new LambdaQueryWrapper<>();
        if (reportDate != null) {
            wrapper.eq(BizDailyCashReport::getReportDate, reportDate);
        }
        wrapper.orderByDesc(BizDailyCashReport::getReportDate).last("LIMIT 1");
        BizDailyCashReport report = reportMapper.selectOne(wrapper);
        if (report == null) {
            throw new BusinessException(404, "暂无日报数据，请先生成");
        }
        return report;
    }

    /**
     * 近 N 日日报趋势（供老板看板折线图）
     */
    public List<BizDailyCashReport> trend(int days) {
        if (days < 1 || days > 90) {
            throw new BusinessException(400, "查询天数需在1-90之间");
        }
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        return reportMapper.selectList(new LambdaQueryWrapper<BizDailyCashReport>()
                .ge(BizDailyCashReport::getReportDate, from)
                .orderByAsc(BizDailyCashReport::getReportDate));
    }

    /**
     * 大额支出明细（当日超门槛的支出流水，供日报下钻）
     */
    public List<BizBankFlow> largeOutflows(LocalDate reportDate, BigDecimal threshold) {
        LocalDate date = reportDate != null ? reportDate : LocalDate.now();
        BigDecimal t = threshold != null ? threshold : DEFAULT_LARGE_THRESHOLD;
        return flowMapper.selectList(new LambdaQueryWrapper<BizBankFlow>()
                .eq(BizBankFlow::getFlowDate, date)
                .eq(BizBankFlow::getDirection, BizBankFlow.DIRECTION_OUT)
                .ge(BizBankFlow::getAmount, t)
                .orderByDesc(BizBankFlow::getAmount));
    }
}
