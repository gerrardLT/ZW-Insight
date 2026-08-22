package com.zwinsight.site.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.site.domain.BizInspection;
import com.zwinsight.site.domain.BizRectification;
import com.zwinsight.site.mapper.BizInspectionMapper;
import com.zwinsight.site.mapper.BizRectificationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RectificationService（检查整改）单元测试
 *
 * 覆盖场景:
 * - 提交整改：检查记录存在性/PENDING 状态校验、SUBMITTED 回写与催办标记清除
 * - 审批整改：状态校验、APPROVED 回写与催办标记清除
 * - 催办标记清除异常不阻断主流程
 */
@ExtendWith(MockitoExtension.class)
class RectificationServiceTest {

    @Mock
    private BizRectificationMapper rectificationMapper;

    @Mock
    private BizInspectionMapper inspectionMapper;

    @Mock
    private ReminderDeduplicationService reminderDeduplicationService;

    @InjectMocks
    private RectificationService rectificationService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizRectification.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizInspection.class);
    }

    private BizInspection pendingInspection(Long id) {
        BizInspection inspection = new BizInspection();
        inspection.setId(id);
        inspection.setProjectId(10L);
        inspection.setRectificationStatus("PENDING");
        return inspection;
    }

    @Test
    @DisplayName("提交整改：检查记录不存在抛异常")
    void submit_inspectionNotFound_throwsException() {
        when(inspectionMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> rectificationService.submit(999L, new BizRectification()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("检查记录不存在");
    }

    @Test
    @DisplayName("提交整改：非 PENDING 状态拒绝提交")
    void submit_notPending_rejected() {
        BizInspection inspection = pendingInspection(1L);
        inspection.setRectificationStatus("APPROVED");
        when(inspectionMapper.selectById(1L)).thenReturn(inspection);

        assertThatThrownBy(() -> rectificationService.submit(1L, new BizRectification()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不允许提交整改");
    }

    @Test
    @DisplayName("提交整改：回写 SUBMITTED 状态（端点审批模式，不发起流程）")
    void submit_success_updatesBoth() {
        BizInspection inspection = pendingInspection(1L);
        when(inspectionMapper.selectById(1L)).thenReturn(inspection);
        BizRectification rectification = new BizRectification();
        rectification.setId(2L);

        rectificationService.submit(1L, rectification);

        // 整改记录：关联字段 + SUBMITTED（P1 修复：移除对不存在的 rectification_approval 流程调用）
        assertThat(rectification.getInspectionId()).isEqualTo(1L);
        assertThat(rectification.getProjectId()).isEqualTo(10L);
        assertThat(rectification.getStatus()).isEqualTo("SUBMITTED");
        verify(rectificationMapper).insert(rectification);

        // 检查记录：整改状态 SUBMITTED + 整改日期
        assertThat(inspection.getRectificationStatus()).isEqualTo("SUBMITTED");
        assertThat(inspection.getRectificationDate()).isEqualTo(LocalDate.now());
        verify(inspectionMapper).updateById(inspection);

        // 催办标记清除
        verify(reminderDeduplicationService).clearMarks(1L);
    }

    @Test
    @DisplayName("提交整改：清除催办标记异常不阻断主流程")
    void submit_clearMarksFails_flowContinues() {
        BizInspection inspection = pendingInspection(1L);
        when(inspectionMapper.selectById(1L)).thenReturn(inspection);
        doThrow(new RuntimeException("redis down"))
                .when(reminderDeduplicationService).clearMarks(1L);
        BizRectification rectification = new BizRectification();
        rectification.setId(2L);

        rectificationService.submit(1L, rectification);

        assertThat(rectification.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("审批整改：不存在抛异常")
    void approve_notFound_throwsException() {
        when(rectificationMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> rectificationService.approve(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整改记录不存在");
    }

    @Test
    @DisplayName("审批整改：非已提交状态拒绝审批")
    void approve_notSubmitted_rejected() {
        BizRectification rectification = new BizRectification();
        rectification.setId(2L);
        rectification.setStatus("DRAFT");
        when(rectificationMapper.selectById(2L)).thenReturn(rectification);

        assertThatThrownBy(() -> rectificationService.approve(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已提交状态可审批");
    }

    @Test
    @DisplayName("审批整改：通过后检查记录置 APPROVED 并清除催办标记")
    void approve_success_updatesInspectionAndClearsMarks() {
        BizRectification rectification = new BizRectification();
        rectification.setId(2L);
        rectification.setInspectionId(1L);
        rectification.setStatus("SUBMITTED");
        when(rectificationMapper.selectById(2L)).thenReturn(rectification);
        BizInspection inspection = pendingInspection(1L);
        inspection.setRectificationStatus("SUBMITTED");
        when(inspectionMapper.selectById(1L)).thenReturn(inspection);

        rectificationService.approve(2L);

        assertThat(rectification.getStatus()).isEqualTo("APPROVED");
        verify(rectificationMapper).updateById(rectification);
        assertThat(inspection.getRectificationStatus()).isEqualTo("APPROVED");
        verify(inspectionMapper).updateById(inspection);
        verify(reminderDeduplicationService).clearMarks(1L);
    }

    @Test
    @DisplayName("审批整改：检查记录不存在时仅更新整改记录")
    void approve_inspectionNotFound_onlyUpdatesRectification() {
        BizRectification rectification = new BizRectification();
        rectification.setId(2L);
        rectification.setInspectionId(1L);
        rectification.setStatus("SUBMITTED");
        when(rectificationMapper.selectById(2L)).thenReturn(rectification);
        when(inspectionMapper.selectById(1L)).thenReturn(null);

        rectificationService.approve(2L);

        assertThat(rectification.getStatus()).isEqualTo("APPROVED");
        verify(inspectionMapper, org.mockito.Mockito.never()).updateById(org.mockito.ArgumentMatchers.any(BizInspection.class));
    }

    @Test
    @DisplayName("查询整改记录：按检查ID过滤并倒序返回")
    void listByInspection_returnsRecords() {
        BizRectification r1 = new BizRectification();
        r1.setInspectionId(1L);
        r1.setStatus("APPROVED");
        when(rectificationMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(r1));

        java.util.List<BizRectification> result = rectificationService.listByInspection(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("查询整改记录：无记录返回空列表而非 null")
    void listByInspection_emptyWhenNone() {
        when(rectificationMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());

        java.util.List<BizRectification> result = rectificationService.listByInspection(999L);

        assertThat(result).isEmpty();
    }
}
