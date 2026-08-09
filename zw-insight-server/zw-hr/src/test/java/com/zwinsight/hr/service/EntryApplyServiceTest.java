package com.zwinsight.hr.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.hr.domain.EntryApply;
import com.zwinsight.hr.domain.RegularApply;
import com.zwinsight.hr.mapper.EntryApplyMapper;
import com.zwinsight.hr.mapper.RegularApplyMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EntryApplyService（入职）和 RegularApplyService（转正）测试
 * 
 * 覆盖场景:
 * - 入职申请流程
 * - 背景调查验证
 * - 账号自动创建
 * - 重复工号检测
 * - 身份证号格式校验
 * - 转正评估逻辑
 */
@ExtendWith(MockitoExtension.class)
class HrApplyTests {

    @Mock
    private EntryApplyMapper entryApplyMapper;

    @Mock
    private RegularApplyMapper regularApplyMapper;

    private EntryApplyService entryApplyService;
    private RegularApplyService regularApplyService;

    @BeforeEach
    void setUp() {
        entryApplyService = new EntryApplyService(entryApplyMapper);
        regularApplyService = new RegularApplyService(regularApplyMapper);
    }

    // ==================== 入职申请测试 ====================

    @Test
    @DisplayName("提交入职申请成功")
    void submitEntryApplication_success() {
        // Given
        EntryApply entryApply = createEntryApply(1L, "张三", "zhangsan@company.com", "engineering");
        
        when(entryApplyMapper.insert(any(EntryApply.class))).thenReturn(1);

        // When
        boolean result = entryApplyService.submit(entryApply);

        // Then
        assertTrue(result);
        verify(entryApplyMapper).insert(any(EntryApply.class));
    }

    @Test
    @DisplayName("入职申请 - 重复工号检测")
    void submitEntryApplication_duplicateEmployeeId_throwsException() {
        // Given
        EntryApply entryApply = createEntryApply(1L, "张三", "zhangsan@company.com", "engineering");
        entryApply.setEmployeeId("EMP2024001");
        
        when(entryApplyMapper.selectByEmployeeId("EMP2024001")).thenReturn(Optional.of(createEntryApply(999L, "李四", null, null)));

        // When & Then
        assertThatThrownBy(() -> entryApplyService.submit(entryApply))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("工号已存在");
    }

    @Test
    @DisplayName("入职申请 - 身份证号格式校验失败")
    void submitEntryApplication_invalidIdCard_throwsException() {
        // Given
        EntryApply entryApply = createEntryApply(1L, "张三", "zhangsan@company.com", "engineering");
        entryApply.setIdCard("123456"); // 无效 ID
        
        when(entryApplyMapper.selectByEmployeeId(null)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> entryApplyService.submit(entryApply))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("身份证号格式不正确");
    }

    @Test
    @DisplayName("入职申请 - 背景调查未通过应被拒绝")
    void submitEntryApplication_backgroundCheckFailed_rejected() {
        // Given
        EntryApply entryApply = createEntryApply(1L, "张三", "zhangsan@company.com", "engineering");
        entryApply.setBackgroundCheckStatus("FAILED");
        
        when(entryApplyMapper.insert(any(EntryApply.class))).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> entryApplyService.submit(entryApply))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("背景调查未通过");
    }

    @Test
    @DisplayName("查询入职申请列表")
    void getEntryApplications_returnsList() {
        // Given
        List<EntryApply> applications = List.of(
            createEntryApply(1L, "张三", null, "engineering"),
            createEntryApply(2L, "李四", null, "sales")
        );
        
        when(entryApplyMapper.selectList(any())).thenReturn(applications);

        // When
        List<EntryApply> result = entryApplyService.getList();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(entryApplyMapper).selectList(any());
    }

    @Test
    @DisplayName("批准入职申请并创建账号")
    void approveEntryApplication_createsAccount() {
        // Given
        Long applyId = 1L;
        EntryApply pendingApply = createEntryApply(applyId, "张三", "zhangsan@company.com", "engineering");
        pendingApply.setStatus("PENDING");
        
        when(entryApplyMapper.selectById(applyId)).thenReturn(Optional.of(pendingApply));
        doNothing().when(entryApplyMapper).updateById(any(EntryApply.class));

        // When
        boolean result = entryApplyService.approve(applyId, "admin");

        // Then
        assertTrue(result);
        verify(entryApplyMapper).updateById(any(EntryApply.class));
    }

    // ==================== 转正申请测试 ====================

    @Test
    @DisplayName("提交转正申请成功")
    void submitRegularApplication_success() {
        // Given
        RegularApply regularApply = createRegularApply(1L, "张三", "EMP2024001");
        regularApply.setProbationPeriod(3);
        regularApply.setProbationScore(85.5);
        
        when(regularApplyMapper.insert(any(RegularApply.class))).thenReturn(1);

        // When
        boolean result = regularApplyService.submit(regularApply);

        // Then
        assertTrue(result);
        verify(regularApplyMapper).insert(any(RegularApply.class));
    }

    @Test
    @DisplayName("转正评估 - 提前转正特批流程")
    void regularApply_earlyRegularization_approved() {
        // Given
        RegularApply regularApply = createRegularApply(1L, "张三", "EMP2024001");
        regularApply.setProbationPeriod(3);
        regularApply.setActualProbationDays(45); // 远少于正常 90 天
        regularApply.setPerformanceScore(95.0);
        regularApply.setRequestEarlyTermination(true);
        
        when(regularApplyMapper.insert(any(RegularApply.class))).thenReturn(1);

        // When
        boolean result = regularApplyService.submit(regularApply);

        // Then
        assertTrue(result);
        // 应触发特批流程
        assertTrue(regularApply.getIsEarlyTermination());
    }

    @Test
    @DisplayName("转正评估 - 绩效不达标延长试用期")
    void regularApplication_performanceTooLow_extensionRequired() {
        // Given
        RegularApply regularApply = createRegularApply(1L, "张三", "EMP2024001");
        regularApply.setProbationPeriod(3);
        regularApply.setPerformanceScore(59.0); // 低于 60 分
        
        when(regularApplyMapper.insert(any(RegularApply.class))).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> regularApplyService.submit(regularApply))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("绩效评分不得低于 60 分");
    }

    @Test
    @DisplayName("转正申请 - 试用期未满不可提交")
    void regularApplication_probationNotEnded_throwsException() {
        // Given
        RegularApply regularApply = createRegularApply(1L, "张三", "EMP2024001");
        regularApply.setStartDate(System.currentTimeMillis() + 10000000000L); // 未来日期
        
        when(regularApplyMapper.insert(any(RegularApply.class))).thenReturn(1);

        // When & Then
        assertThatThrownBy(() -> regularApplyService.submit(regularApply))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("尚未完成试用期");
    }

    @Test
    @DisplayName("批准转正申请")
    void approveRegularApplication_success() {
        // Given
        Long applyId = 1L;
        RegularApply pendingApply = createRegularApply(applyId, "张三", "EMP2024001");
        pendingApply.setStatus("PENDING");
        
        when(regularApplyMapper.selectById(applyId)).thenReturn(Optional.of(pendingApply));
        doNothing().when(regularApplyMapper).updateById(any(RegularApply.class));

        // When
        boolean result = regularApplyService.approve(applyId);

        // Then
        assertTrue(result);
        verify(regularApplyMapper).updateById(any(RegularApply.class));
    }

    // ==================== 辅助方法 ====================

    private EntryApply createEntryApply(Long id, String name, String email, String department) {
        EntryApply apply = new EntryApply();
        apply.setId(id);
        apply.setName(name);
        apply.setEmail(email);
        apply.setDepartment(department);
        apply.setEmployeeId(null);
        apply.setIdCard(null);
        apply.setBackgroundCheckStatus("PASSED");
        apply.setStatus("PENDING");
        return apply;
    }

    private RegularApply createRegularApply(Long id, String name, String employeeId) {
        RegularApply apply = new RegularApply();
        apply.setId(id);
        apply.setName(name);
        apply.setEmployeeId(employeeId);
        apply.setStartDate(System.currentTimeMillis() - 90L * 24L * 60L * 60L * 1000L); // 90 天前
        apply.setEndDate(System.currentTimeMillis());
        return apply;
    }
}
