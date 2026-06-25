package com.zwinsight.labor.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.dto.SalaryDetailExcelDTO;
import com.zwinsight.labor.dto.SalarySummaryExcelDTO;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import com.zwinsight.labor.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 薪资统计服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryStatisticsServiceImpl implements SalaryStatisticsService {

    private final BizLaborPayrollMapper payrollMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizTeamMapper teamMapper;
    private final BizLaborRosterMapper rosterMapper;

    private static final String STATUS_APPROVED = "APPROVED";
    private static final String ORDER_TYPE_FIXED = "FIXED";
    private static final String ORDER_TYPE_TEMPORARY = "TEMPORARY";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public SalaryStatsSummary getStatsByTeam(Long projectId, String month) {
        validateParams(projectId, month);

        // 解析月份获取周期范围
        YearMonth yearMonth = YearMonth.parse(month, MONTH_FORMATTER);
        LocalDate periodStart = yearMonth.atDay(1);
        LocalDate periodEnd = yearMonth.atEndOfMonth();

        // 查询该项目该月份所有已审批工资单
        List<BizLaborPayroll> approvedPayrolls = queryApprovedPayrolls(projectId, periodStart, periodEnd, null);

        // 空数据处理
        if (approvedPayrolls.isEmpty()) {
            throw new BusinessException("该月份暂无已审批的薪资数据");
        }

        // 查询该项目所有班组
        Map<Long, BizTeam> teamMap = getTeamMap(projectId);

        // 查询已审批工单获取出勤数据
        List<BizWorkOrder> approvedOrders = queryApprovedWorkOrders(projectId, periodStart, periodEnd, null);

        // 按班组分组汇总
        Map<Long, List<BizLaborPayroll>> payrollByTeam = approvedPayrolls.stream()
                .collect(Collectors.groupingBy(BizLaborPayroll::getTeamId));

        // 按班组分组工单（统计人数）
        Map<Long, List<BizWorkOrder>> ordersByTeam = approvedOrders.stream()
                .collect(Collectors.groupingBy(BizWorkOrder::getTeamId));

        List<TeamSalaryVO> teamList = new ArrayList<>();
        BigDecimal totalPayable = BigDecimal.ZERO;
        BigDecimal totalDeduction = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal fixedPayable = BigDecimal.ZERO;
        BigDecimal temporaryPayable = BigDecimal.ZERO;
        int totalHeadCount = 0;

        for (Map.Entry<Long, List<BizLaborPayroll>> entry : payrollByTeam.entrySet()) {
            Long teamId = entry.getKey();
            List<BizLaborPayroll> teamPayrolls = entry.getValue();
            BizTeam team = teamMap.get(teamId);

            // 计算班组汇总
            BigDecimal teamPayable = sumField(teamPayrolls, BizLaborPayroll::getTotalSettlement);
            BigDecimal teamPaid = sumField(teamPayrolls, BizLaborPayroll::getTotalPaid);
            BigDecimal teamDeduction = teamPayable.subtract(teamPaid.add(sumField(teamPayrolls, BizLaborPayroll::getUnpaid)));
            BigDecimal teamActual = teamPayable.subtract(teamDeduction);

            // 统计班组人数（去重工人）
            List<BizWorkOrder> teamOrders = ordersByTeam.getOrDefault(teamId, Collections.emptyList());
            int headCount = (int) teamOrders.stream()
                    .map(BizWorkOrder::getWorkerId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            // 确定用工类型
            String orderType = teamPayrolls.stream()
                    .map(BizLaborPayroll::getOrderType)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(ORDER_TYPE_FIXED);

            TeamSalaryVO teamVO = new TeamSalaryVO();
            teamVO.setTeamId(teamId);
            teamVO.setTeamName(team != null ? team.getTeamName() : "未知班组");
            teamVO.setLeaderName(team != null ? team.getLeaderName() : "");
            teamVO.setHeadCount(headCount);
            teamVO.setTotalPayable(scale2(teamPayable));
            teamVO.setTotalDeduction(scale2(teamDeduction));
            teamVO.setTotalActual(scale2(teamActual));
            teamVO.setOrderType(orderType);
            teamList.add(teamVO);

            totalPayable = totalPayable.add(teamPayable);
            totalDeduction = totalDeduction.add(teamDeduction);
            totalActual = totalActual.add(teamActual);
            totalHeadCount += headCount;

            // 分类统计
            if (ORDER_TYPE_FIXED.equals(orderType)) {
                fixedPayable = fixedPayable.add(teamPayable);
            } else {
                temporaryPayable = temporaryPayable.add(teamPayable);
            }
        }

        SalaryStatsSummary summary = new SalaryStatsSummary();
        summary.setProjectId(projectId);
        summary.setMonth(month);
        summary.setTeamCount(teamList.size());
        summary.setTotalHeadCount(totalHeadCount);
        summary.setTotalPayable(scale2(totalPayable));
        summary.setTotalDeduction(scale2(totalDeduction));
        summary.setTotalActual(scale2(totalActual));
        summary.setFixedPayable(scale2(fixedPayable));
        summary.setTemporaryPayable(scale2(temporaryPayable));
        summary.setTeamList(teamList);
        return summary;
    }

    @Override
    public PageResult<SalaryDetailVO> getTeamDetail(Long projectId, String month, Long teamId, int page, int size) {
        validateParams(projectId, month);
        if (teamId == null) {
            throw new BusinessException("班组ID不能为空");
        }

        YearMonth yearMonth = YearMonth.parse(month, MONTH_FORMATTER);
        LocalDate periodStart = yearMonth.atDay(1);
        LocalDate periodEnd = yearMonth.atEndOfMonth();

        // 查询该班组已审批工单（分页）
        Page<BizWorkOrder> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizWorkOrder::getProjectId, projectId)
                .eq(BizWorkOrder::getTeamId, teamId)
                .eq(BizWorkOrder::getStatus, STATUS_APPROVED)
                .ge(BizWorkOrder::getWorkDate, periodStart)
                .le(BizWorkOrder::getWorkDate, periodEnd);

        // 先查询全部工单用于聚合
        List<BizWorkOrder> allOrders = workOrderMapper.selectList(wrapper);

        if (allOrders.isEmpty()) {
            throw new BusinessException("该班组在该月份暂无已审批的用工数据");
        }

        // 查询花名册获取身份证信息
        Map<Long, BizLaborRoster> rosterMap = getRosterMap(projectId, teamId);

        // 按工人分组聚合
        Map<Long, List<BizWorkOrder>> ordersByWorker = allOrders.stream()
                .filter(o -> o.getWorkerId() != null)
                .collect(Collectors.groupingBy(BizWorkOrder::getWorkerId));

        List<SalaryDetailVO> detailList = new ArrayList<>();
        for (Map.Entry<Long, List<BizWorkOrder>> entry : ordersByWorker.entrySet()) {
            Long workerId = entry.getKey();
            List<BizWorkOrder> workerOrders = entry.getValue();

            SalaryDetailVO detail = buildSalaryDetail(workerId, workerOrders, rosterMap);
            detailList.add(detail);
        }

        // 手动分页
        int total = detailList.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<SalaryDetailVO> pageList = detailList.subList(fromIndex, toIndex);

        PageResult<SalaryDetailVO> result = new PageResult<>();
        result.setRecords(pageList);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setPages((total + size - 1) / size);
        return result;
    }

    @Override
    public SalaryMonthlyReport generateMonthlyReport(Long projectId, String month) {
        // 获取统计汇总
        SalaryStatsSummary summary = getStatsByTeam(projectId, month);
        SalaryCompareVO compare = getCompareData(projectId, month);

        SalaryMonthlyReport report = new SalaryMonthlyReport();
        report.setProjectId(projectId);
        report.setMonth(month);
        report.setTeamCount(summary.getTeamCount());
        report.setTotalHeadCount(summary.getTotalHeadCount());
        report.setTotalPayable(summary.getTotalPayable());
        report.setTotalDeduction(summary.getTotalDeduction());
        report.setTotalActual(summary.getTotalActual());
        report.setFixedSubtotal(summary.getFixedPayable());
        report.setTemporarySubtotal(summary.getTemporaryPayable());
        report.setMomRate(compare.getMomRate());
        report.setYoyRate(compare.getYoyRate());
        report.setTeamSummaryList(summary.getTeamList());

        // 获取全量工人明细
        YearMonth yearMonth = YearMonth.parse(month, MONTH_FORMATTER);
        LocalDate periodStart = yearMonth.atDay(1);
        LocalDate periodEnd = yearMonth.atEndOfMonth();
        List<BizWorkOrder> allOrders = queryApprovedWorkOrders(projectId, periodStart, periodEnd, null);
        Map<Long, BizLaborRoster> rosterMap = getRosterMapByProject(projectId);

        Map<Long, List<BizWorkOrder>> ordersByWorker = allOrders.stream()
                .filter(o -> o.getWorkerId() != null)
                .collect(Collectors.groupingBy(BizWorkOrder::getWorkerId));

        List<SalaryDetailVO> detailList = ordersByWorker.entrySet().stream()
                .map(e -> buildSalaryDetail(e.getKey(), e.getValue(), rosterMap))
                .collect(Collectors.toList());

        report.setDetailList(detailList);
        return report;
    }

    @Override
    public SalaryCompareVO getCompareData(Long projectId, String month) {
        validateParams(projectId, month);

        YearMonth currentYM = YearMonth.parse(month, MONTH_FORMATTER);
        YearMonth previousYM = currentYM.minusMonths(1);
        YearMonth lastYearYM = currentYM.minusYears(1);

        // 当月总额
        BigDecimal currentAmount = getMonthTotal(projectId, currentYM, null);
        BigDecimal previousMonthAmount = getMonthTotal(projectId, previousYM, null);
        BigDecimal lastYearAmount = getMonthTotal(projectId, lastYearYM, null);

        // 分类统计
        BigDecimal currentFixed = getMonthTotal(projectId, currentYM, ORDER_TYPE_FIXED);
        BigDecimal currentTemporary = getMonthTotal(projectId, currentYM, ORDER_TYPE_TEMPORARY);
        BigDecimal previousFixed = getMonthTotal(projectId, previousYM, ORDER_TYPE_FIXED);
        BigDecimal previousTemporary = getMonthTotal(projectId, previousYM, ORDER_TYPE_TEMPORARY);
        BigDecimal lastYearFixed = getMonthTotal(projectId, lastYearYM, ORDER_TYPE_FIXED);
        BigDecimal lastYearTemporary = getMonthTotal(projectId, lastYearYM, ORDER_TYPE_TEMPORARY);

        // 计算变化率
        BigDecimal momRate = calculateChangeRate(currentAmount, previousMonthAmount);
        BigDecimal yoyRate = calculateChangeRate(currentAmount, lastYearAmount);

        SalaryCompareVO vo = new SalaryCompareVO();
        vo.setCurrentMonth(month);
        vo.setCurrentAmount(scale2(currentAmount));
        vo.setPreviousMonthAmount(scale2(previousMonthAmount));
        vo.setLastYearAmount(scale2(lastYearAmount));
        vo.setMomRate(momRate);
        vo.setYoyRate(yoyRate);
        vo.setCurrentFixedAmount(scale2(currentFixed));
        vo.setCurrentTemporaryAmount(scale2(currentTemporary));
        vo.setPreviousFixedAmount(scale2(previousFixed));
        vo.setPreviousTemporaryAmount(scale2(previousTemporary));
        vo.setLastYearFixedAmount(scale2(lastYearFixed));
        vo.setLastYearTemporaryAmount(scale2(lastYearTemporary));
        return vo;
    }

    @Override
    public void exportReport(Long projectId, String month, HttpServletResponse response) {
        validateParams(projectId, month);

        SalaryMonthlyReport report = generateMonthlyReport(projectId, month);

        // 构造 Sheet1 数据 - 汇总表
        List<SalarySummaryExcelDTO> summaryData = report.getTeamSummaryList().stream()
                .map(team -> {
                    SalarySummaryExcelDTO dto = new SalarySummaryExcelDTO();
                    dto.setTeamName(team.getTeamName());
                    dto.setLeaderName(team.getLeaderName());
                    dto.setOrderTypeLabel(getOrderTypeLabel(team.getOrderType()));
                    dto.setHeadCount(team.getHeadCount());
                    dto.setTotalPayable(team.getTotalPayable());
                    dto.setTotalDeduction(team.getTotalDeduction());
                    dto.setTotalActual(team.getTotalActual());
                    return dto;
                })
                .collect(Collectors.toList());

        // 构造 Sheet2 数据 - 班组明细表
        Map<Long, BizTeam> teamMap = getTeamMap(projectId);
        List<SalaryDetailExcelDTO> detailData = report.getDetailList().stream()
                .map(detail -> {
                    SalaryDetailExcelDTO dto = new SalaryDetailExcelDTO();
                    // 查找该工人所属班组名称
                    dto.setTeamName(findTeamNameForWorker(detail.getWorkerId(), projectId, teamMap));
                    dto.setWorkerName(detail.getWorkerName());
                    dto.setIdCardLast4(detail.getIdCardLast4());
                    dto.setOrderTypeLabel(getOrderTypeLabel(detail.getOrderType()));
                    dto.setAttendanceDays(detail.getAttendanceDays());
                    dto.setOvertimeHours(detail.getOvertimeHours());
                    dto.setPayable(detail.getPayable());
                    dto.setDeduction(detail.getDeduction());
                    dto.setActual(detail.getActual());
                    return dto;
                })
                .collect(Collectors.toList());

        // 设置响应头
        String fileName = "薪资报表_" + month + ".xlsx";
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName);

            ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build();

            // Sheet1: 汇总表
            WriteSheet summarySheet = EasyExcel.writerSheet(0, "汇总表")
                    .head(SalarySummaryExcelDTO.class)
                    .build();
            excelWriter.write(summaryData, summarySheet);

            // Sheet2: 班组明细表
            WriteSheet detailSheet = EasyExcel.writerSheet(1, "班组明细表")
                    .head(SalaryDetailExcelDTO.class)
                    .build();
            excelWriter.write(detailData, detailSheet);

            excelWriter.finish();
        } catch (Exception e) {
            log.error("导出薪资报表失败", e);
            throw new BusinessException("导出报表失败：" + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 参数校验
     */
    private void validateParams(Long projectId, String month) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (month == null || month.isBlank()) {
            throw new BusinessException("统计月份不能为空");
        }
        try {
            YearMonth.parse(month, MONTH_FORMATTER);
        } catch (Exception e) {
            throw new BusinessException("月份格式不正确，应为YYYY-MM格式");
        }
    }

    /**
     * 查询已审批工资单
     */
    private List<BizLaborPayroll> queryApprovedPayrolls(Long projectId, LocalDate periodStart, LocalDate periodEnd, String orderType) {
        LambdaQueryWrapper<BizLaborPayroll> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizLaborPayroll::getProjectId, projectId)
                .eq(BizLaborPayroll::getStatus, STATUS_APPROVED)
                .ge(BizLaborPayroll::getPeriodStart, periodStart)
                .le(BizLaborPayroll::getPeriodEnd, periodEnd);
        if (orderType != null) {
            wrapper.eq(BizLaborPayroll::getOrderType, orderType);
        }
        return payrollMapper.selectList(wrapper);
    }

    /**
     * 查询已审批工单
     */
    private List<BizWorkOrder> queryApprovedWorkOrders(Long projectId, LocalDate periodStart, LocalDate periodEnd, String orderType) {
        LambdaQueryWrapper<BizWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizWorkOrder::getProjectId, projectId)
                .eq(BizWorkOrder::getStatus, STATUS_APPROVED)
                .ge(BizWorkOrder::getWorkDate, periodStart)
                .le(BizWorkOrder::getWorkDate, periodEnd);
        if (orderType != null) {
            wrapper.eq(BizWorkOrder::getOrderType, orderType);
        }
        return workOrderMapper.selectList(wrapper);
    }

    /**
     * 获取月度总额
     */
    private BigDecimal getMonthTotal(Long projectId, YearMonth yearMonth, String orderType) {
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();
        List<BizLaborPayroll> payrolls = queryApprovedPayrolls(projectId, start, end, orderType);
        return sumField(payrolls, BizLaborPayroll::getTotalSettlement);
    }

    /**
     * 计算变化率：(current - previous) / previous * 100%，精确到小数点后1位
     * 如果 previous 为 0，返回 null（无法计算）
     */
    private BigDecimal calculateChangeRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 获取项目班组 Map
     */
    private Map<Long, BizTeam> getTeamMap(Long projectId) {
        LambdaQueryWrapper<BizTeam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizTeam::getProjectId, projectId);
        List<BizTeam> teams = teamMapper.selectList(wrapper);
        return teams.stream().collect(Collectors.toMap(BizTeam::getId, t -> t, (a, b) -> a));
    }

    /**
     * 获取班组花名册 Map（按工人ID）
     */
    private Map<Long, BizLaborRoster> getRosterMap(Long projectId, Long teamId) {
        LambdaQueryWrapper<BizLaborRoster> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizLaborRoster::getProjectId, projectId)
                .eq(BizLaborRoster::getTeamId, teamId);
        List<BizLaborRoster> rosters = rosterMapper.selectList(wrapper);
        return rosters.stream().collect(Collectors.toMap(BizLaborRoster::getId, r -> r, (a, b) -> a));
    }

    /**
     * 获取项目花名册 Map
     */
    private Map<Long, BizLaborRoster> getRosterMapByProject(Long projectId) {
        LambdaQueryWrapper<BizLaborRoster> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizLaborRoster::getProjectId, projectId);
        List<BizLaborRoster> rosters = rosterMapper.selectList(wrapper);
        return rosters.stream().collect(Collectors.toMap(BizLaborRoster::getId, r -> r, (a, b) -> a));
    }

    /**
     * 构建工人薪资明细
     */
    private SalaryDetailVO buildSalaryDetail(Long workerId, List<BizWorkOrder> workerOrders, Map<Long, BizLaborRoster> rosterMap) {
        SalaryDetailVO detail = new SalaryDetailVO();
        detail.setWorkerId(workerId);

        // 工人姓名（取第一条工单中的 workerName）
        String workerName = workerOrders.stream()
                .map(BizWorkOrder::getWorkerName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("未知");
        detail.setWorkerName(workerName);

        // 身份证后4位（从花名册查询）
        BizLaborRoster roster = rosterMap.get(workerId);
        if (roster != null && roster.getIdCard() != null && roster.getIdCard().length() >= 4) {
            detail.setIdCardLast4(roster.getIdCard().substring(roster.getIdCard().length() - 4));
        } else {
            detail.setIdCardLast4("****");
        }

        // 出勤天数（按工作日期去重计数）
        int attendanceDays = (int) workerOrders.stream()
                .map(BizWorkOrder::getWorkDate)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        detail.setAttendanceDays(attendanceDays);

        // 加班工时汇总
        BigDecimal overtimeHours = workerOrders.stream()
                .map(BizWorkOrder::getOvertime)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        detail.setOvertimeHours(overtimeHours.setScale(1, RoundingMode.HALF_UP));

        // 应发金额（正常工时 + 加班收入）
        BigDecimal payable = workerOrders.stream()
                .map(BizWorkOrder::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        detail.setPayable(scale2(payable));

        // 扣款金额（工资单实际扣款，简化处理为 0，后续可扩展）
        detail.setDeduction(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        // 实发金额 = 应发 - 扣款
        detail.setActual(scale2(payable.subtract(detail.getDeduction())));

        // 用工类型
        String orderType = workerOrders.stream()
                .map(BizWorkOrder::getOrderType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ORDER_TYPE_FIXED);
        detail.setOrderType(orderType);

        return detail;
    }

    /**
     * 查找工人所属班组名称
     */
    private String findTeamNameForWorker(Long workerId, Long projectId, Map<Long, BizTeam> teamMap) {
        LambdaQueryWrapper<BizWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizWorkOrder::getProjectId, projectId)
                .eq(BizWorkOrder::getWorkerId, workerId)
                .last("LIMIT 1");
        List<BizWorkOrder> orders = workOrderMapper.selectList(wrapper);
        if (!orders.isEmpty() && orders.get(0).getTeamId() != null) {
            BizTeam team = teamMap.get(orders.get(0).getTeamId());
            return team != null ? team.getTeamName() : "未知班组";
        }
        return "未知班组";
    }

    /**
     * 用工类型标签转换
     */
    private String getOrderTypeLabel(String orderType) {
        if (ORDER_TYPE_FIXED.equals(orderType)) {
            return "自有劳务";
        } else if (ORDER_TYPE_TEMPORARY.equals(orderType)) {
            return "零星用工";
        }
        return "未分类";
    }

    /**
     * 汇总字段
     */
    private <T> BigDecimal sumField(List<T> list, java.util.function.Function<T, BigDecimal> getter) {
        return list.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * BigDecimal scale=2, ROUND_HALF_UP
     */
    private BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
