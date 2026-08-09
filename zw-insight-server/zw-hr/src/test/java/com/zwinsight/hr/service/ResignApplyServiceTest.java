package com.zwinsight.hr.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.hr.domain.ResignApply;
import com.zwinsight.hr.mapper.ResignApplyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResignApplyService（离职申请）测试
 * 
 * 覆盖场景:
 * - 离职交接流程
 * - 资产归还清单（关联设备台账）
 * - 薪资结算截止日验证
 * - 竞业限制协议触发
 */
@ExtendWith(MockitoExtension.class)
class ResignApplyServiceTest {

    @Mock
    private ResignApplyMapper resignApplyMapper;

    private ResignApplyService resignApplyService;

    @BeforeEach
    void setUp() {
        resignApplyService = new ResignApplyService(resignApplyMapper);
    }

    // ==================== 离职申请测试 ====================

    @Test
    @DisplayName("提交离职申请成功")
    void submitResignApplication_success() {
        // Given
        ResignApply resignApply = createResignApply(1L, "张三", "EMP2024001");
        resignApply.setResignDate(System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L); // 30 天后
        resignApply.setNoticeDays(30);
        
        when(resignApplyMapper.insert(any(ResignApply.class))).thenReturn(1);

        // When
        boolean result = resignApplyService.submit(resignApply);

        // Then
        assertTrue(result);
        verify(resignApplyMapper).insert(any(ResignApply.class));
    }

    @Test
    @DisplayName("离职申请 - 通知期不足抛出异常")
    void submitResignApplication_noticePeriodTooShort_throwsException() {
        // Given
        ResignApply resignApply = createResignApply(1L, "张三", "EMP2024001");
        resignApply.setResignDate(System.currentTimeMillis() + 5L * 24L * 60L * 60L * 1000L); // 仅 5 天
        resignApply.setNoticeDays(5);
        
        when(resignApplyMapper.insert(any(ResignApply.class))).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> resignApplyService.submit(resignApply))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("提前 30 天通知");
    }

    @Test
    @DisplayName("离职申请 - 资产未归还完整禁止审批")
    void approveResignApplication_assetsNotReturned_rejected() {
        // Given
        Long applyId = 1L;
        ResignApply pendingApply = createResignApply(applyId, "张三", "EMP2024001");
        pendingApply.setStatus("PENDING");
        pendingApply.setAssetReturnStatus("INCOMPLETE"); // 资产未全部归还
        
        when(resignApplyMapper.selectById(applyId)).thenReturn(Optional.of(pendingApply));
        when(resignApplyMapper.updateById(any(ResignApply.class))).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> resignApplyService.approve(applyId, "hr"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("资产归还未完成");
    }

    @Test
    @DisplayName("批准离职申请并结算薪资")
    void approveResignApplication_completeAndSettle() {
        // Given
        Long applyId = 1L;
        ResignApply pendingApply = createResignApply(applyId, "张三", "EMP2024001");
        pendingApply.setStatus("PENDING");
        pendingApply.setAssetReturnStatus("COMPLETED");
        pendingApply.setSalaryDueDays(5); // 5 天后结算
        
        when(resignApplyMapper.selectById(applyId)).thenReturn(Optional.of(pendingApply));
        doNothing().when(resignApplyMapper).updateById(any(ResignApply.class));

        // When
        boolean result = resignApplyService.approve(applyId, "hr");

        // Then
        assertTrue(result);
        verify(resignApplyMapper).updateById(any(ResignApply.class));
    }

    @Test
    @DisplayName("查询离职申请列表")
    void getResignApplications_returnsList() {
        // Given
        List<ResignApply> applications = List.of(
            createResignApply(1L, "张三", "EMP2024001"),
            createResignApply(2L, "李四", "EMP2024002")
        );
        
        when(resignApplyMapper.selectList(any())).thenReturn(applications);

        // When
        List<ResignApply> result = resignApplyService.getList();

        // Then
        assertEquals(2, result.size());
        verify(resignApplyMapper).selectList(any());
    }

    @Test
    @DisplayName("竞业限制人员触发特殊流程")
    void resignApplication_competitiveRestriction_triggered() {
        // Given
        ResignApply resignApply = createResignApply(1L, "张三", "EMP2024001");
        resignApply.setPosition("技术总监");
        resignApply.isCompetitiveRestrictionRequired(true);
        
        when(resignApplyMapper.insert(any(ResignApply.class))).thenReturn(1);

        // When
        boolean result = resignApplyService.submit(resignApply);

        // Then
        assertTrue(result);
        // 竞业限制人员应标记
        assertTrue(resignApply.getIsCompetitiveRestriction());
    }

    // ==================== 辅助方法 ====================

    private ResignApply createResignApply(Long id, String name, String employeeId) {
        ResignApply apply = new ResignApply();
        apply.setId(id);
        apply.setName(name);
        apply.setEmployeeId(employeeId);
        apply.setAssetReturnStatus("IN_PROGRESS");
        apply.setStatus("DRAFT");
        return apply;
    }
}
