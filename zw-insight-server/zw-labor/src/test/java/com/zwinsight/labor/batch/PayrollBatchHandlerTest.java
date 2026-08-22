package com.zwinsight.labor.batch;

import com.alibaba.excel.EasyExcel;
import com.zwinsight.file.batch.domain.ImportResult;
import com.zwinsight.file.batch.dto.PayrollExcelDTO;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.labor.domain.BizLaborPayroll;
import com.zwinsight.labor.domain.BizTeam;
import com.zwinsight.labor.mapper.BizLaborPayrollMapper;
import com.zwinsight.labor.mapper.BizTeamMapper;
import com.zwinsight.labor.service.LaborPayrollService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PayrollBatchHandler 单元测试（P0 工资单批量导入）
 * <p>
 * 使用真实 xlsx 走完整 EasyExcel 解析链路（不 mock 解析器），
 * 覆盖：正常导入（teamId 解析回填 + 项目回退班组项目）、周期重叠拒绝、班组不存在拒绝。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollBatchHandlerTest {

    @TempDir
    Path tempDir;

    @Mock
    private BizTeamMapper teamMapper;

    @Mock
    private BizLaborPayrollMapper payrollMapper;

    @Mock
    private LaborPayrollService payrollService;

    @InjectMocks
    private PayrollBatchHandler handler;

    /** 用 EasyExcel 真实写 xlsx 后走监听器解析链路 */
    @SuppressWarnings("unchecked")
    private ImportResult runImport(Long projectId, List<PayrollExcelDTO> rows) throws Exception {
        Path file = tempDir.resolve("payroll-" + System.nanoTime() + ".xlsx");
        EasyExcel.write(file.toFile(), PayrollExcelDTO.class).sheet().doWrite(rows);
        byte[] bytes = Files.readAllBytes(file);
        Files.delete(file);

        AbstractImportListener<PayrollExcelDTO> listener =
                (AbstractImportListener<PayrollExcelDTO>) handler.createImportListener(projectId);
        EasyExcel.read(new ByteArrayInputStream(bytes), PayrollExcelDTO.class, listener)
                .sheet().doRead();
        return listener.getImportResult();
    }

    private PayrollExcelDTO validRow() {
        PayrollExcelDTO row = new PayrollExcelDTO();
        row.setTeamName("木工一班");
        row.setOrderType("固定");
        row.setPeriodStart("2026-01-01");
        row.setPeriodEnd("2026-01-31");
        return row;
    }

    private BizTeam team() {
        BizTeam team = new BizTeam();
        team.setId(5L);
        team.setTeamName("木工一班");
        team.setProjectId(10L);
        return team;
    }

    @Test
    @DisplayName("正常导入 - teamId 解析回填，projectId 缺省回退班组所属项目")
    void happyPath_resolvesTeamAndFallsBackToTeamProject() throws Exception {
        BizTeam team = team();
        when(teamMapper.selectOne(any())).thenReturn(team);
        when(teamMapper.selectById(5L)).thenReturn(team);
        when(payrollMapper.selectCount(any())).thenReturn(0L);

        ImportResult result = runImport(null, List.of(validRow()));

        assertThat(result.isAllSuccess()).isTrue();
        assertThat(result.getSuccessRows()).isEqualTo(1);

        ArgumentCaptor<BizLaborPayroll> captor = ArgumentCaptor.forClass(BizLaborPayroll.class);
        verify(payrollService).save(captor.capture());
        BizLaborPayroll saved = captor.getValue();
        assertThat(saved.getTeamId()).isEqualTo(5L);
        assertThat(saved.getProjectId()).isEqualTo(10L);
        assertThat(saved.getOrderType()).isEqualTo("FIXED");
        assertThat(saved.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(saved.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    @DisplayName("显式传入 projectId - 优先于班组所属项目")
    void explicitProjectId_wins() throws Exception {
        BizTeam team = team();
        when(teamMapper.selectOne(any())).thenReturn(team);
        when(teamMapper.selectById(5L)).thenReturn(team);
        when(payrollMapper.selectCount(any())).thenReturn(0L);

        ImportResult result = runImport(88L, List.of(validRow()));

        assertThat(result.getSuccessRows()).isEqualTo(1);
        ArgumentCaptor<BizLaborPayroll> captor = ArgumentCaptor.forClass(BizLaborPayroll.class);
        verify(payrollService).save(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("DB 周期重叠 - 行级拒绝且不保存")
    void dbPeriodOverlap_rowRejected() throws Exception {
        when(teamMapper.selectOne(any())).thenReturn(team());
        when(payrollMapper.selectCount(any())).thenReturn(1L);

        ImportResult result = runImport(null, List.of(validRow()));

        assertThat(result.getFailedRows()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getErrorMessage()).contains("周期重叠");
        verify(payrollService, never()).save(any());
    }

    @Test
    @DisplayName("班组不存在 - 行级拒绝且不保存")
    void unknownTeam_rowRejected() throws Exception {
        when(teamMapper.selectOne(any())).thenReturn(null);

        ImportResult result = runImport(null, List.of(validRow()));

        assertThat(result.getFailedRows()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getErrorMessage()).contains("不存在");
        verify(payrollService, never()).save(any());
    }

    @Test
    @DisplayName("文件内重复周期行 - 第二行拒绝（批内查重）")
    void duplicatePeriodInFile_secondRowRejected() throws Exception {
        BizTeam team = team();
        when(teamMapper.selectOne(any())).thenReturn(team);
        when(teamMapper.selectById(5L)).thenReturn(team);
        when(payrollMapper.selectCount(any())).thenReturn(0L);

        ImportResult result = runImport(null, List.of(validRow(), validRow()));

        assertThat(result.getSuccessRows()).isEqualTo(1);
        assertThat(result.getFailedRows()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getErrorMessage()).contains("重复周期");
    }
}
