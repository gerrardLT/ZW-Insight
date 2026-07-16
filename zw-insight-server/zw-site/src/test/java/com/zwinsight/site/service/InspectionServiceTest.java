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
}
