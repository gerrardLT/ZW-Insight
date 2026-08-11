package com.zwinsight.site.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizInspectionDetail;
import com.zwinsight.site.mapper.BizInspectionDetailMapper;
import com.zwinsight.site.mapper.BizInspectionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * InspectionService 单元测试（检查明细持久化相关）
 */
@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock
    private BizInspectionMapper inspectionMapper;

    @Mock
    private BizInspectionDetailMapper inspectionDetailMapper;

    @InjectMocks
    private InspectionService inspectionService;

    @Test
    @DisplayName("新增：默认无问题并持久化明细")
    void testSave_withDetails() {
        BizInspection inspection = new BizInspection();
        inspection.setProjectId(100L);

        BizInspectionDetail d1 = new BizInspectionDetail();
        d1.setItemName("模板支撑");
        BizInspectionDetail d2 = new BizInspectionDetail();
        d2.setItemName("钢筋绑扎");
        d2.setCheckResult("PASS");
        inspection.setDetails(List.of(d1, d2));

        inspectionService.save(inspection);

        assertThat(inspection.getHasProblem()).isEqualTo(0);
        verify(inspectionMapper).insert(inspection);

        ArgumentCaptor<BizInspectionDetail> captor = ArgumentCaptor.forClass(BizInspectionDetail.class);
        verify(inspectionDetailMapper, times(2)).insert(captor.capture());
        List<BizInspectionDetail> inserted = captor.getAllValues();
        // 未指定结果默认 NOT_CHECKED，已指定的保留
        assertThat(inserted.get(0).getCheckResult()).isEqualTo("NOT_CHECKED");
        assertThat(inserted.get(0).getSortOrder()).isEqualTo(1);
        assertThat(inserted.get(1).getCheckResult()).isEqualTo("PASS");
        assertThat(inserted.get(1).getSortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("新增：无明细时仅插入主表")
    void testSave_noDetails() {
        BizInspection inspection = new BizInspection();
        inspection.setProjectId(100L);

        inspectionService.save(inspection);

        verify(inspectionMapper).insert(inspection);
        verify(inspectionDetailMapper, never()).insert(any());
    }

    @Test
    @DisplayName("详情：不存在抛异常")
    void testGetDetail_notFound() {
        when(inspectionMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> inspectionService.getDetail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检查记录不存在");
    }

    @Test
    @DisplayName("详情：返回主表并加载明细")
    void testGetDetail_withDetails() {
        BizInspection inspection = new BizInspection();
        inspection.setId(1L);
        when(inspectionMapper.selectById(1L)).thenReturn(inspection);

        BizInspectionDetail detail = new BizInspectionDetail();
        detail.setItemName("模板支撑");
        when(inspectionDetailMapper.selectList(any())).thenReturn(List.of(detail));

        BizInspection result = inspectionService.getDetail(1L);

        assertThat(result.getDetails()).hasSize(1);
        assertThat(result.getDetails().get(0).getItemName()).isEqualTo("模板支撑");
    }

    @Test
    @DisplayName("更新明细：删除后重建")
    void testUpdateDetails_replace() {
        BizInspection inspection = new BizInspection();
        inspection.setId(1L);
        when(inspectionMapper.selectById(1L)).thenReturn(inspection);

        BizInspectionDetail d1 = new BizInspectionDetail();
        d1.setItemName("模板支撑");

        inspectionService.updateDetails(1L, List.of(d1));

        verify(inspectionDetailMapper).deleteByInspectionId(1L);
        verify(inspectionDetailMapper).insert(any());
    }

    @Test
    @DisplayName("更新明细：检查记录不存在抛异常")
    void testUpdateDetails_notFound() {
        when(inspectionMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> inspectionService.updateDetails(999L, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检查记录不存在");
    }

    // ============ submitResults / assignRectification（原零测试，2026-08-11 补） ============

    @Test
    @DisplayName("提交检查结果：按 key 选择性更新字段并落库")
    void testSubmitResults_updatesProvidedFields() {
        BizInspection inspection = new BizInspection();
        inspection.setId(1L);
        when(inspectionMapper.selectById(1L)).thenReturn(inspection);

        java.util.Map<String, Object> results = new java.util.HashMap<>();
        results.put("hasProblem", 1);
        results.put("problemDescription", "钢筋间距超标");

        inspectionService.submitResults(1L, results);

        assertThat(inspection.getHasProblem()).isEqualTo(1);
        assertThat(inspection.getProblemDescription()).isEqualTo("钢筋间距超标");
        verify(inspectionMapper).updateById(inspection);
    }

    @Test
    @DisplayName("提交检查结果：记录不存在抛异常")
    void testSubmitResults_notFound() {
        when(inspectionMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> inspectionService.submitResults(999L, java.util.Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检查记录不存在");

        verify(inspectionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("指派整改：有问题记录写入责任人/期限并置 PENDING")
    void testAssignRectification_success() {
        BizInspection inspection = new BizInspection();
        inspection.setId(1L);
        inspection.setHasProblem(1);
        when(inspectionMapper.selectById(1L)).thenReturn(inspection);

        inspectionService.assignRectification(1L, 200L, java.time.LocalDate.of(2026, 9, 1));

        assertThat(inspection.getResponsiblePersonId()).isEqualTo(200L);
        assertThat(inspection.getRectificationDeadline()).isEqualTo(java.time.LocalDate.of(2026, 9, 1));
        assertThat(inspection.getRectificationStatus()).isEqualTo("PENDING");
        verify(inspectionMapper).updateById(inspection);
    }

    @Test
    @DisplayName("指派整改：无问题记录拒绝（无需整改）")
    void testAssignRectification_noProblem_rejected() {
        BizInspection inspection = new BizInspection();
        inspection.setId(1L);
        inspection.setHasProblem(0);
        when(inspectionMapper.selectById(1L)).thenReturn(inspection);

        assertThatThrownBy(() -> inspectionService.assignRectification(1L, 200L, java.time.LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无需整改");

        verify(inspectionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("指派整改：记录不存在抛异常")
    void testAssignRectification_notFound() {
        when(inspectionMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> inspectionService.assignRectification(999L, 200L, java.time.LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检查记录不存在");
    }
}
