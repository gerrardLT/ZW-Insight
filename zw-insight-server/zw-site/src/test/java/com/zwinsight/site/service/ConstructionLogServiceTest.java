package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.site.domain.BizConstructionLog;
import com.zwinsight.site.mapper.BizConstructionLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * ConstructionLogService 单元测试
 * 覆盖：施工日志 CRUD（分页查询、新增、更新、删除）
 */
@ExtendWith(MockitoExtension.class)
class ConstructionLogServiceTest {

    @Mock
    private BizConstructionLogMapper logMapper;

    @InjectMocks
    private ConstructionLogService constructionLogService;

    private BizConstructionLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleLog = new BizConstructionLog();
        sampleLog.setId(1L);
        sampleLog.setProjectId(100L);
        sampleLog.setLogDate(LocalDate.of(2026, 7, 1));
        sampleLog.setWeather("晴");
        sampleLog.setTemperature("28℃");
        sampleLog.setWind("微风");
        sampleLog.setWorkerCount(50);
        sampleLog.setProductionRecord("浇筑基础承台C30混凝土200m³");
        sampleLog.setTechnicalRecord("混凝土坍落度检测合格");
    }

    // =====================================================================
    // 分页查询测试
    // =====================================================================

    @Nested
    @DisplayName("分页查询")
    class PageQueryTests {

        @Test
        @DisplayName("按项目ID和日期范围分页查询 - 返回正确分页结果")
        void page_withProjectIdAndDateRange_returnsPageResult() {
            // given
            Page<BizConstructionLog> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.singletonList(sampleLog));
            mockPage.setTotal(1L);
            when(logMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // when
            PageResult<BizConstructionLog> result = constructionLogService.page(
                    1, 10, 100L,
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 31));

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getRecords().get(0).getProjectId()).isEqualTo(100L);
            verify(logMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("不传项目ID和日期范围 - 查询全部施工日志")
        void page_withoutFilters_returnsAll() {
            // given
            Page<BizConstructionLog> mockPage = new Page<>(1, 10);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0L);
            when(logMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // when
            PageResult<BizConstructionLog> result = constructionLogService.page(1, 10, null, null, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isZero();
        }

        @Test
        @DisplayName("仅传项目ID不传日期 - 按项目过滤")
        void page_withOnlyProjectId_filtersCorrectly() {
            // given
            Page<BizConstructionLog> mockPage = new Page<>(1, 20);
            mockPage.setRecords(List.of(sampleLog));
            mockPage.setTotal(1L);
            when(logMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            // when
            PageResult<BizConstructionLog> result = constructionLogService.page(1, 20, 100L, null, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
        }
    }

    // =====================================================================
    // 新增施工日志测试
    // =====================================================================

    @Nested
    @DisplayName("新增施工日志")
    class SaveTests {

        @Test
        @DisplayName("新增施工日志 - 正常保存")
        void save_validLog_success() {
            // given
            BizConstructionLog newLog = new BizConstructionLog();
            newLog.setProjectId(200L);
            newLog.setLogDate(LocalDate.of(2026, 7, 5));
            newLog.setWeather("多云");
            newLog.setWorkerCount(30);
            newLog.setProductionRecord("钢筋绑扎");

            when(logMapper.insert(any(BizConstructionLog.class))).thenReturn(1);

            // when
            constructionLogService.save(newLog);

            // then
            verify(logMapper).insert(argThat(log ->
                    log.getProjectId().equals(200L) &&
                    log.getLogDate().equals(LocalDate.of(2026, 7, 5)) &&
                    "多云".equals(log.getWeather()) &&
                    log.getWorkerCount() == 30
            ));
        }

        @Test
        @DisplayName("新增施工日志 - 包含完整字段")
        void save_withAllFields_success() {
            // given
            when(logMapper.insert(any(BizConstructionLog.class))).thenReturn(1);

            // when
            constructionLogService.save(sampleLog);

            // then
            verify(logMapper).insert(argThat(log ->
                    "晴".equals(log.getWeather()) &&
                    "28℃".equals(log.getTemperature()) &&
                    "微风".equals(log.getWind()) &&
                    log.getWorkerCount() == 50 &&
                    log.getProductionRecord().contains("混凝土") &&
                    log.getTechnicalRecord().contains("坍落度")
            ));
        }
    }

    // =====================================================================
    // 更新施工日志测试
    // =====================================================================

    @Nested
    @DisplayName("更新施工日志")
    class UpdateTests {

        @Test
        @DisplayName("更新已存在的施工日志 - 成功")
        void update_existingLog_success() {
            // given
            BizConstructionLog updateLog = new BizConstructionLog();
            updateLog.setId(1L);
            updateLog.setWeather("阴");
            updateLog.setWorkerCount(45);

            when(logMapper.selectById(1L)).thenReturn(sampleLog);
            when(logMapper.updateById(any(BizConstructionLog.class))).thenReturn(1);

            // when
            constructionLogService.update(updateLog);

            // then
            verify(logMapper).selectById(1L);
            verify(logMapper).updateById(updateLog);
        }

        @Test
        @DisplayName("更新不存在的施工日志 - 抛出BusinessException")
        void update_nonExistingLog_throwsException() {
            // given
            BizConstructionLog updateLog = new BizConstructionLog();
            updateLog.setId(999L);

            when(logMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> constructionLogService.update(updateLog))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("施工日志不存在");

            verify(logMapper, never()).updateById(any());
        }
    }

    // =====================================================================
    // 删除施工日志测试
    // =====================================================================

    @Nested
    @DisplayName("删除施工日志")
    class DeleteTests {

        @Test
        @DisplayName("删除已存在的施工日志 - 成功")
        void delete_existingLog_success() {
            // given
            when(logMapper.selectById(1L)).thenReturn(sampleLog);
            when(logMapper.deleteById(1L)).thenReturn(1);

            // when
            constructionLogService.delete(1L);

            // then
            verify(logMapper).selectById(1L);
            verify(logMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除不存在的施工日志 - 抛出BusinessException")
        void delete_nonExistingLog_throwsException() {
            // given
            when(logMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThatThrownBy(() -> constructionLogService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("施工日志不存在");

            verify(logMapper, never()).deleteById(anyLong());
        }
    }
}
