package com.zwinsight.labor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.domain.BizWorkOrder;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.labor.mapper.BizWorkOrderMapper;
import com.zwinsight.labor.vo.SalaryCompareVO;
import com.zwinsight.labor.vo.SalaryDetailVO;
import com.zwinsight.labor.vo.SalaryMonthlyReport;
import com.zwinsight.labor.vo.SalaryStatsSummary;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SalaryStatisticsServiceImpl 单元测试
 * <p>薪资统计为纯聚合逻辑：按班组汇总工资单、按工人聚合工单、环比同比变化率、Excel 导出。</p>
 */
@ExtendWith(MockitoExtension.class)
class SalaryStatisticsServiceImplTest {

    @Mock
    private BizLaborPayrollMapper payrollMapper;

    @Mock
    private BizWorkOrderMapper workOrderMapper;

    @Mock
    private BizTeamMapper teamMapper;

    @Mock
    private BizLaborRosterMapper rosterMapper;

    @InjectMocks
    private SalaryStatisticsServiceImpl service;

    private BizLaborPayroll payroll(Long teamId, String orderType, String settlement, String paid, String unpaid) {
        BizLaborPayroll p = new BizLaborPayroll();
        p.setProjectId(1L);
        p.setTeamId(teamId);
        p.setStatus("APPROVED");
        p.setOrderType(orderType);
        p.setPeriodStart(LocalDate.of(2026, 7, 1));
        p.setPeriodEnd(LocalDate.of(2026, 7, 31));
        p.setTotalSettlement(new BigDecimal(settlement));
        p.setTotalPaid(new BigDecimal(paid));
        p.setUnpaid(new BigDecimal(unpaid));
        return p;
    }

    private BizWorkOrder order(Long teamId, Long workerId, String workerName, String date, String amount, String overtime, String type) {
        BizWorkOrder o = new BizWorkOrder();
        o.setProjectId(1L);
        o.setTeamId(teamId);
        o.setWorkerId(workerId);
        o.setWorkerName(workerName);
        o.setStatus("APPROVED");
        o.setWorkDate(LocalDate.parse(date));
        o.setTotalAmount(amount == null ? null : new BigDecimal(amount));
        o.setOvertime(overtime == null ? null : new BigDecimal(overtime));
        o.setOrderType(type);
        return o;
    }

    private BizTeam team(Long id, String name, String leader) {
        BizTeam t = new BizTeam();
        t.setId(id);
        t.setProjectId(1L);
        t.setTeamName(name);
        t.setLeaderName(leader);
        return t;
    }

    private BizLaborRoster roster(Long workerId, String idCard) {
        BizLaborRoster r = new BizLaborRoster();
        r.setId(workerId); // rosterMap 以 roster.id 为键，业务上即工人ID
        r.setProjectId(1L);
        r.setIdCard(idCard);
        return r;
    }

    @Nested
    @DisplayName("getStatsByTeam 按班组统计")
    class StatsByTeamTests {

        @Test
        @DisplayName("参数校验 - 项目ID/月份为空/格式错误抛异常")
        void validateParams_throws() {
            assertThatThrownBy(() -> service.getStatsByTeam(null, "2026-07"))
                    .hasMessageContaining("项目ID不能为空");
            assertThatThrownBy(() -> service.getStatsByTeam(1L, " "))
                    .hasMessageContaining("统计月份不能为空");
            assertThatThrownBy(() -> service.getStatsByTeam(1L, "2026/07"))
                    .hasMessageContaining("月份格式不正确");
        }

        @Test
        @DisplayName("无已审批薪资数据 - 抛业务异常")
        void noApprovedPayrolls_throws() {
            when(payrollMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> service.getStatsByTeam(1L, "2026-07"))
                    .hasMessageContaining("该月份暂无已审批的薪资数据");
        }

        @Test
        @DisplayName("正常汇总 - 两班组分类统计、扣款=应发-已付-未付、人数按工单去重")
        void success_aggregatesByTeam() {
            // 班组1 FIXED：应发 10000，已付 8000，未付 1000 → 扣款 1000
            // 班组2 TEMPORARY：应发 5000，已付 5000，未付 0 → 扣款 0
            when(payrollMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(
                            payroll(10L, "FIXED", "10000", "8000", "1000"),
                            payroll(20L, "TEMPORARY", "5000", "5000", "0")));
            when(teamMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.singletonList(team(10L, "木工班", "张班")));
            // 班组1 两名工人（其中一人工单重复按日去重），班组2 无工单
            when(workOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(
                            order(10L, 100L, "工人甲", "2026-07-01", "300", null, "FIXED"),
                            order(10L, 100L, "工人甲", "2026-07-02", "300", null, "FIXED"),
                            order(10L, 101L, "工人乙", "2026-07-01", "200", null, "FIXED")));

            SalaryStatsSummary summary = service.getStatsByTeam(1L, "2026-07");

            assertThat(summary.getTeamCount()).isEqualTo(2);
            assertThat(summary.getTotalHeadCount()).isEqualTo(2);
            assertThat(summary.getTotalPayable()).isEqualByComparingTo("15000.00");
            assertThat(summary.getTotalDeduction()).isEqualByComparingTo("1000.00");
            assertThat(summary.getTotalActual()).isEqualByComparingTo("14000.00");
            assertThat(summary.getFixedPayable()).isEqualByComparingTo("10000.00");
            assertThat(summary.getTemporaryPayable()).isEqualByComparingTo("5000.00");

            // 班组2 无 team 记录 → 未知班组；班组1 名称正常
            assertThat(summary.getTeamList())
                    .extracting("teamName")
                    .containsExactlyInAnyOrder("木工班", "未知班组");
        }
    }

    @Nested
    @DisplayName("getTeamDetail 班组明细")
    class TeamDetailTests {

        @Test
        @DisplayName("班组ID为空/无工单 - 抛异常")
        void guardCases_throws() {
            assertThatThrownBy(() -> service.getTeamDetail(1L, "2026-07", null, 1, 10))
                    .hasMessageContaining("班组ID不能为空");

            when(workOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            assertThatThrownBy(() -> service.getTeamDetail(1L, "2026-07", 10L, 1, 10))
                    .hasMessageContaining("暂无已审批的用工数据");
        }

        @Test
        @DisplayName("正常聚合 - 按工人分组、身份证后4位、出勤去重、加班汇总、手动分页")
        void success_aggregatesByWorker() {
            when(workOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(
                            order(10L, 100L, "工人甲", "2026-07-01", "300", "2", "FIXED"),
                            order(10L, 100L, "工人甲", "2026-07-01", "150", "1", "FIXED"),
                            order(10L, 101L, null, "2026-07-02", "200", null, null)));
            when(rosterMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.singletonList(roster(100L, "110101199001011234")));

            PageResult<SalaryDetailVO> result = service.getTeamDetail(1L, "2026-07", 10L, 1, 10);

            assertThat(result.getTotal()).isEqualTo(2);
            SalaryDetailVO worker1 = result.getRecords().stream()
                    .filter(d -> d.getWorkerId().equals(100L)).findFirst().orElseThrow();
            assertThat(worker1.getWorkerName()).isEqualTo("工人甲");
            assertThat(worker1.getIdCardLast4()).isEqualTo("1234");
            assertThat(worker1.getAttendanceDays()).isEqualTo(1); // 同一天两条工单去重
            assertThat(worker1.getOvertimeHours()).isEqualByComparingTo("3.0");
            assertThat(worker1.getPayable()).isEqualByComparingTo("450.00");
            assertThat(worker1.getActual()).isEqualByComparingTo("450.00");

            SalaryDetailVO worker2 = result.getRecords().stream()
                    .filter(d -> d.getWorkerId().equals(101L)).findFirst().orElseThrow();
            assertThat(worker2.getWorkerName()).isEqualTo("未知");
            assertThat(worker2.getIdCardLast4()).isEqualTo("****");
            assertThat(worker2.getOrderType()).isEqualTo("FIXED"); // null 兜底为 FIXED
        }
    }

    @Nested
    @DisplayName("getCompareData 环比同比")
    class CompareDataTests {

        @Test
        @DisplayName("变化率计算 - (当期-基期)/基期×100，基期为 0 返回 null")
        void changeRates_calculated() {
            // 当月 10000、上月 8000、去年同月 0
            // payrollMapper.selectList 按调用顺序返回：当月总/上月/去年/当月FIXED/当月TEMP/上月FIXED/上月TEMP/去年FIXED/去年TEMP
            BizLaborPayroll cur = payroll(10L, "FIXED", "10000", "10000", "0");
            BizLaborPayroll prev = payroll(10L, "FIXED", "8000", "8000", "0");
            when(payrollMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(
                            Collections.singletonList(cur),   // 当月总
                            Collections.singletonList(prev),  // 上月总
                            Collections.emptyList(),          // 去年同月（无数据 → 0）
                            Collections.singletonList(cur),   // 当月 FIXED
                            Collections.emptyList(),          // 当月 TEMPORARY
                            Collections.singletonList(prev),  // 上月 FIXED
                            Collections.emptyList(),          // 上月 TEMPORARY
                            Collections.emptyList(),          // 去年 FIXED
                            Collections.emptyList());         // 去年 TEMPORARY

            SalaryCompareVO vo = service.getCompareData(1L, "2026-07");

            // 环比 = (10000-8000)/8000×100 = 25.0
            assertThat(vo.getMomRate()).isEqualByComparingTo("25.0");
            // 同比基期 0 → null
            assertThat(vo.getYoyRate()).isNull();
            assertThat(vo.getCurrentAmount()).isEqualByComparingTo("10000.00");
            assertThat(vo.getCurrentFixedAmount()).isEqualByComparingTo("10000.00");
            assertThat(vo.getCurrentTemporaryAmount()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("generateMonthlyReport / exportReport")
    class ReportTests {

        private void stubFullData() {
            when(payrollMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenAnswer(inv -> Collections.singletonList(payroll(10L, "FIXED", "10000", "9000", "0")));
            when(teamMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.singletonList(team(10L, "木工班", "张班")));
            when(workOrderMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.singletonList(
                            order(10L, 100L, "工人甲", "2026-07-01", "300", "1", "FIXED")));
            when(rosterMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.singletonList(roster(100L, "110101199001011234")));
        }

        @Test
        @DisplayName("generateMonthlyReport - 汇总+对比+明细组合完整")
        void monthlyReport_combinesAll() {
            stubFullData();

            SalaryMonthlyReport report = service.generateMonthlyReport(1L, "2026-07");

            assertThat(report.getProjectId()).isEqualTo(1L);
            assertThat(report.getMonth()).isEqualTo("2026-07");
            assertThat(report.getTeamCount()).isEqualTo(1);
            assertThat(report.getTotalPayable()).isEqualByComparingTo("10000.00");
            assertThat(report.getTeamSummaryList()).hasSize(1);
            assertThat(report.getDetailList()).hasSize(1);
            assertThat(report.getDetailList().get(0).getWorkerName()).isEqualTo("工人甲");
        }

        @Test
        @DisplayName("exportReport - 参数非法抛异常")
        void exportReport_invalidParams_throws() {
            assertThatThrownBy(() -> service.exportReport(null, "2026-07", mock(HttpServletResponse.class)))
                    .hasMessageContaining("项目ID不能为空");
        }

        @Test
        @DisplayName("exportReport - 正常导出：写响应流、设置下载头、双 Sheet 写入成功")
        void exportReport_success_writesExcel() throws Exception {
            stubFullData();

            HttpServletResponse response = mock(HttpServletResponse.class);
            ServletOutputStream sos = mock(ServletOutputStream.class);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doAnswer(inv -> {
                byte[] bytes = inv.getArgument(0);
                int off = inv.getArgument(1);
                int len = inv.getArgument(2);
                bos.write(bytes, off, len);
                return null;
            }).when(sos).write(any(byte[].class), anyInt(), anyInt());
            when(response.getOutputStream()).thenReturn(sos);

            service.exportReport(1L, "2026-07", response);

            verify(response).setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            // 文件名经 URLEncoder 编码：断言下载头格式 + 解码后含报表名
            org.mockito.ArgumentCaptor<String> headerCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(response).setHeader(eq("Content-Disposition"), headerCaptor.capture());
            assertThat(headerCaptor.getValue()).startsWith("attachment;filename*=utf-8''");
            assertThat(java.net.URLDecoder.decode(headerCaptor.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("薪资报表_2026-07");
            // 真实写入的 xlsx 字节流非空且为 ZIP 魔数（xlsx 即 zip）
            assertThat(bos.size()).isGreaterThan(100);
            assertThat(bos.toByteArray()[0]).isEqualTo((byte) 'P');
            assertThat(bos.toByteArray()[1]).isEqualTo((byte) 'K');
        }
    }
}
