package com.zwinsight.site.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizReminderLog;
import com.zwinsight.site.dto.ReminderStatsVO;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.BizReminderLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReminderLogServiceImpl（催办日志）单元测试
 *
 * 覆盖场景:
 * - 保存催办日志
 * - 按检查记录查询催办历史
 * - 项目催办统计：无检查记录早返回 / 正常聚合超期数、催办数、升级数
 */
@ExtendWith(MockitoExtension.class)
class ReminderLogServiceImplTest {

    @Mock
    private BizReminderLogMapper reminderLogMapper;

    @Mock
    private BizInspectionMapper inspectionMapper;

    @InjectMocks
    private ReminderLogServiceImpl reminderLogService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizReminderLog.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizInspection.class);
    }

    @Test
    @DisplayName("保存催办日志：透传 insert")
    void saveLog_delegatesToMapper() {
        BizReminderLog log = new BizReminderLog();
        log.setInspectionId(1L);

        reminderLogService.saveLog(log);

        verify(reminderLogMapper).insert(log);
    }

    @Test
    @DisplayName("查询催办历史：按检查记录ID返回日志列表")
    void getLogsByInspectionId_returnsLogs() {
        BizReminderLog log = new BizReminderLog();
        log.setInspectionId(1L);
        when(reminderLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(log));

        List<BizReminderLog> result = reminderLogService.getLogsByInspectionId(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("项目催办统计：无检查记录时仅返回超期数")
    void getStatsByProjectId_noInspections_returnsEarly() {
        when(inspectionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(inspectionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ReminderStatsVO vo = reminderLogService.getStatsByProjectId(10L);

        assertThat(vo.getTotalOverdueCount()).isEqualTo(2L);
        assertThat(vo.getTotalReminderCount()).isZero();
        assertThat(vo.getEscalatedCount()).isZero();
    }

    @Test
    @DisplayName("项目催办统计：聚合催办总数与升级通知数")
    void getStatsByProjectId_aggregatesCounts() {
        when(inspectionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        BizInspection inspection = new BizInspection();
        inspection.setId(1L);
        when(inspectionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(inspection));
        when(reminderLogMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(5L)
                .thenReturn(2L);

        ReminderStatsVO vo = reminderLogService.getStatsByProjectId(10L);

        assertThat(vo.getTotalOverdueCount()).isEqualTo(1L);
        assertThat(vo.getTotalReminderCount()).isEqualTo(5L);
        assertThat(vo.getEscalatedCount()).isEqualTo(2L);
    }
}
