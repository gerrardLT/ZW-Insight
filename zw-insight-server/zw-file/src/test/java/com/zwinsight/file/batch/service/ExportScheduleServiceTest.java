package com.zwinsight.file.batch.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.batch.domain.BizExportSchedule;
import com.zwinsight.file.batch.mapper.BizExportScheduleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时导出配置服务单元测试（Cron 校验 + 手动执行 + 模块清单）
 */
@ExtendWith(MockitoExtension.class)
class ExportScheduleServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BizExportSchedule.class);
    }

    @Mock private BizExportScheduleMapper scheduleMapper;
    @Mock private BatchImportExportService batchImportExportService;
    @Mock private com.zwinsight.security.service.TenantTaskRunner tenantTaskRunner;

    @org.junit.jupiter.api.BeforeEach
    void stubTenantRunner() {
        // 逐租户执行器透传：直接执行单租户逻辑（lenient：部分用例不走扫描路径）
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            ((java.util.function.LongConsumer) inv.getArgument(1)).accept(9999L);
            return null;
        }).when(tenantTaskRunner).runForActiveTenants(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @InjectMocks
    private ExportScheduleService exportScheduleService;

    @Test
    @DisplayName("创建配置：无效 Cron 抛异常")
    void testSave_invalidCron() {
        BizExportSchedule schedule = new BizExportSchedule();
        schedule.setCronExpression("not-a-cron");

        assertThatThrownBy(() -> exportScheduleService.save(schedule))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cron 表达式无效");
    }

    @Test
    @DisplayName("创建配置：合法 Cron 自动启用并计算下次执行时间")
    void testSave_validCron() {
        BizExportSchedule schedule = new BizExportSchedule();
        schedule.setCronExpression("0 0 2 * * ?");
        schedule.setModuleCode("SUPPLIER");

        exportScheduleService.save(schedule);

        ArgumentCaptor<BizExportSchedule> captor = ArgumentCaptor.forClass(BizExportSchedule.class);
        verify(scheduleMapper).insert(captor.capture());
        assertThat(captor.getValue().getEnabled()).isEqualTo(1);
        assertThat(captor.getValue().getNextExecuteTime()).isNotNull();
    }

    @Test
    @DisplayName("更新配置：不存在抛异常；无效 Cron 抛异常")
    void testUpdate_validation() {
        when(scheduleMapper.selectById(999L)).thenReturn(null);
        BizExportSchedule missing = new BizExportSchedule();
        missing.setId(999L);
        assertThatThrownBy(() -> exportScheduleService.update(missing))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配置不存在");

        BizExportSchedule existing = new BizExportSchedule();
        existing.setId(1L);
        when(scheduleMapper.selectById(1L)).thenReturn(existing);
        BizExportSchedule badCron = new BizExportSchedule();
        badCron.setId(1L);
        badCron.setCronExpression("bad");
        assertThatThrownBy(() -> exportScheduleService.update(badCron))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cron 表达式无效");
    }

    @Test
    @DisplayName("手动执行：配置不存在抛异常；存在时触发异步导出返回任务ID")
    void testExecuteNow() {
        when(scheduleMapper.selectById(999L)).thenReturn(null);
        assertThatThrownBy(() -> exportScheduleService.executeNow(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配置不存在");

        BizExportSchedule schedule = new BizExportSchedule();
        schedule.setId(1L);
        schedule.setModuleCode("SUPPLIER");
        when(scheduleMapper.selectById(1L)).thenReturn(schedule);
        when(batchImportExportService.asyncExport(any(), any())).thenReturn(888L);

        assertThat(exportScheduleService.executeNow(1L)).isEqualTo(888L);
        verify(batchImportExportService).asyncExport(any(), any());
    }

    @Test
    @DisplayName("分页与可导出模块清单")
    void testPageAndModules() {
        Page<BizExportSchedule> page = new Page<>(1, 10);
        page.setRecords(List.of(new BizExportSchedule()));
        page.setTotal(1);
        when(scheduleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        PageResult<BizExportSchedule> result = exportScheduleService.page(1, 10);
        assertThat(result.getRecords()).hasSize(1);

        List<Map<String, String>> modules = exportScheduleService.getAvailableModules();
        assertThat(modules).hasSize(10);
        assertThat(modules.get(0)).containsKeys("code", "name");
    }

    @Test
    @DisplayName("定时扫描：到期启用项执行导出并推进 nextExecuteTime（原零测试，2026-08-11 补）")
    void testScanAndExecute_dueSchedule_executesAndAdvances() {
        BizExportSchedule schedule = new BizExportSchedule();
        schedule.setId(1L);
        schedule.setModuleCode("SUPPLIER");
        schedule.setEnabled(1);
        schedule.setCronExpression("0 0 2 * * ?");
        schedule.setNextExecuteTime(java.time.LocalDateTime.now().minusMinutes(1));
        when(scheduleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(schedule));
        when(batchImportExportService.asyncExport(any(), any())).thenReturn(777L);

        exportScheduleService.scanAndExecuteScheduledExports();

        // 触发导出并回写推进后的下次执行时间
        verify(batchImportExportService).asyncExport(eq("SUPPLIER"), any());
        org.mockito.ArgumentCaptor<BizExportSchedule> captor =
                org.mockito.ArgumentCaptor.forClass(BizExportSchedule.class);
        verify(scheduleMapper).updateById(captor.capture());
        assertThat(captor.getValue().getNextExecuteTime())
                .isAfter(java.time.LocalDateTime.now().minusMinutes(1));
        assertThat(captor.getValue().getLastExecuteTime()).isNotNull();
    }

    @Test
    @DisplayName("定时扫描：无到期项时不执行任何导出")
    void testScanAndExecute_noDueSchedules_noop() {
        when(scheduleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        exportScheduleService.scanAndExecuteScheduledExports();

        verify(batchImportExportService, never()).asyncExport(any(), any());
        verify(scheduleMapper, never()).updateById(any());
    }
}
