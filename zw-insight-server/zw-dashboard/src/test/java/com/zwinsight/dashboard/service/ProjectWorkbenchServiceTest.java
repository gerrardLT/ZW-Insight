package com.zwinsight.dashboard.service;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.mapper.BizRiskRegisterMapper;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.mapper.BizReceivableMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.SysUserProjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectWorkbenchServiceTest {

    @Mock private BizProjectMapper projectMapper;
    @Mock private BizRiskRegisterMapper riskMapper;
    @Mock private BizReceivableMapper receivableMapper;
    @Mock private SysUserProjectMapper userProjectMapper;
    @Mock private CockpitService cockpitService;

    @InjectMocks private ProjectWorkbenchService service;

    // SecurityContextHolder 为静态 ThreadLocal：其他测试类残留 userId 会让「我的项目」口径漂移
    @BeforeEach
    void setUp() {
        SecurityContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    private BizProject project(Long id, String status) {
        BizProject project = new BizProject();
        project.setId(id);
        project.setProjectName("项目" + id);
        project.setStatus(status);
        return project;
    }

    private BizReceivable overdue(Long id, Long projectId, String amount) {
        BizReceivable receivable = new BizReceivable();
        receivable.setId(id);
        receivable.setProjectId(projectId);
        receivable.setStatus("OPEN");
        receivable.setReceivableAmount(new BigDecimal(amount));
        receivable.setWrittenOffAmount(new BigDecimal("100"));
        receivable.setDueDate(LocalDate.now().minusDays(3));
        return receivable;
    }

    private BizRiskRegister risk(Long id, Long projectId, String severity) {
        BizRiskRegister risk = new BizRiskRegister();
        risk.setId(id);
        risk.setProjectId(projectId);
        risk.setSeverity(severity);
        risk.setHandleStatus(BizRiskRegister.HANDLE_OPEN);
        return risk;
    }

    private Map<String, Object> health(Long projectId, String health) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectId", projectId);
        row.put("projectName", "项目" + projectId);
        row.put("status", "CONSTRUCTION");
        row.put("health", health);
        row.put("forecastProfit", new BigDecimal("100"));
        row.put("profitRate", new BigDecimal("0.1"));
        row.put("fundGapAmount", BigDecimal.ZERO);
        row.put("redCount", "RED".equals(health) ? 1L : 0L);
        row.put("yellowCount", "YELLOW".equals(health) ? 1L : 0L);
        row.put("topRisks", List.of("风险A"));
        return row;
    }

    @Test
    @DisplayName("空数据返回 0 与空数组，不返回 null")
    void summary_emptyReturnsZeros() {
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(cockpitService.getProjectHealth()).thenReturn(List.of());
        when(receivableMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.summary(null);

        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertThat(summary.get("total")).isEqualTo(0);
        assertThat(summary.get("mine")).isEqualTo(0L);
        assertThat(summary.get("warning")).isEqualTo(0L);
        assertThat(summary.get("riskOpen")).isEqualTo(0L);
        assertThat(summary.get("overdueReceivableAmount")).isEqualTo(BigDecimal.ZERO);
        assertThat((List<?>) result.get("projects")).isEmpty();
    }

    @Test
    @DisplayName("汇总项目数/在建数/预警数/活跃风险/逾期应收")
    void summary_countsAndOverdueAmount() {
        when(projectMapper.selectList(any())).thenReturn(List.of(
                project(1L, "CONSTRUCTION"), project(2L, "COMPLETED"), project(3L, "FILED")));
        when(cockpitService.getProjectHealth()).thenReturn(List.of(
                health(1L, "RED"), health(2L, "YELLOW")));
        when(riskMapper.selectList(any())).thenReturn(List.of(
                risk(1L, 1L, BizRiskRegister.SEVERITY_RED),
                risk(2L, 2L, BizRiskRegister.SEVERITY_YELLOW),
                risk(3L, 3L, BizRiskRegister.SEVERITY_INFO)));
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                overdue(1L, 1L, "1100"), overdue(2L, 2L, "600")));
        // 我的项目：参与 1/3 且 99 不在数据范围内 → 只计 2 个（与 view=MINE 同口径）
        SecurityContextHolder.setUserId(9L);
        when(userProjectMapper.selectProjectIdsByUserId(9L)).thenReturn(List.of(1L, 3L, 99L));

        Map<String, Object> result = service.summary(null);

        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertThat(summary.get("total")).isEqualTo(3);
        assertThat(summary.get("mine")).isEqualTo(2L);
        assertThat(summary.get("inConstruction")).isEqualTo(1L);
        // 预警=存在活跃风险的项目数（与 view=RISK 同源）：3 号项目健康度 GREEN 但有 INFO 风险，仍计入
        assertThat(summary.get("warning")).isEqualTo(3L);
        assertThat(summary.get("riskOpen")).isEqualTo(3L);
        assertThat(summary.get("overdueReceivableCount")).isEqualTo(2);
        assertThat((BigDecimal) summary.get("overdueReceivableAmount")).isEqualByComparingTo("1500");

        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("projects");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("topRisk")).isEqualTo("风险A");
        assertThat((BigDecimal) rows.get(0).get("overdueReceivableAmount")).isEqualByComparingTo("1000");
        assertThat(rows.get(1).get("overdueReceivableCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("projectIds 只过滤健康度行，不影响 summary 全量口径")
    void summary_projectIdsFilterRowsOnly() {
        when(projectMapper.selectList(any())).thenReturn(List.of(
                project(1L, "CONSTRUCTION"), project(2L, "CONSTRUCTION")));
        when(cockpitService.getProjectHealth()).thenReturn(List.of(
                health(1L, "RED"), health(2L, "GREEN")));
        when(riskMapper.selectList(any())).thenReturn(List.of(risk(1L, 1L, BizRiskRegister.SEVERITY_RED)));
        when(receivableMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.summary("2");

        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertThat(summary.get("total")).isEqualTo(2);
        assertThat(summary.get("warning")).isEqualTo(1L);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("projects");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("projectId")).isEqualTo(2L);
    }

    @Test
    @DisplayName("projectIds 无合法 ID 时返回空行，不退化为全量")
    void summary_invalidProjectIdsReturnsNoRows() {
        when(projectMapper.selectList(any())).thenReturn(List.of(project(1L, "CONSTRUCTION")));
        when(cockpitService.getProjectHealth()).thenReturn(List.of(health(1L, "GREEN")));
        when(riskMapper.selectList(any())).thenReturn(List.of());
        when(receivableMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.summary("abc");

        assertThat((List<?>) result.get("projects")).isEmpty();
    }
}
