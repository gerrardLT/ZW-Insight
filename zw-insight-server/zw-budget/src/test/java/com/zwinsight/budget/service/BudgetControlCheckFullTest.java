package com.zwinsight.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.budget.domain.SysBudgetControlConfig;
import com.zwinsight.budget.dto.BudgetCheckResult;
import com.zwinsight.budget.dto.BudgetControlConfigDTO;
import com.zwinsight.budget.mapper.BizBudgetDetailMapper;
import com.zwinsight.budget.mapper.BudgetOccupiedMapper;
import com.zwinsight.budget.mapper.SysBudgetControlConfigMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BudgetControlConfigService 补充测试（与 BudgetControlConfigServiceTest 互补）
 * <p>覆盖：checkBudget 无预算/预警/通过分支、各成本科目映射、配置校验、getEffectiveConfig 异常兜底。</p>
 */
@ExtendWith(MockitoExtension.class)
class BudgetControlCheckFullTest {

    @Mock
    private SysBudgetControlConfigMapper configMapper;

    @Mock
    private BizBudgetDetailMapper budgetDetailMapper;

    @Mock
    private BudgetOccupiedMapper budgetOccupiedMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private BudgetControlConfigService service;

    /** 桩出指定模式的生效配置（项目级无 → 返回该默认配置） */
    private void stubEffectiveConfig(String controlMode, int threshold) {
        SysBudgetControlConfig config = new SysBudgetControlConfig();
        config.setControlMode(controlMode);
        config.setWarningThreshold(threshold);
        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)     // 项目级无
                .thenReturn(config);  // 默认配置
    }

    // ── checkBudget 分支 ──────────────────────────────────

    @Test
    @DisplayName("checkBudget - 无预算额度：BLOCK 模式拦截、WARN_ONLY 模式警告")
    void checkBudget_noBudget_blockOrWarn() {
        stubEffectiveConfig("BLOCK", 80);
        when(budgetDetailMapper.sumBudgetByProjectAndCategory(1L, "MATERIAL")).thenReturn(null);

        BudgetCheckResult result = service.checkBudget(1L, "MATERIAL", new BigDecimal("1000"));

        assertThat(result.getStatus()).isEqualTo(BudgetCheckResult.Status.BLOCK);
        assertThat(result.getMessage()).contains("未设置预算额度");

        stubEffectiveConfig("WARN_ONLY", 80);
        when(budgetDetailMapper.sumBudgetByProjectAndCategory(1L, "MATERIAL")).thenReturn(BigDecimal.ZERO);

        BudgetCheckResult warnResult = service.checkBudget(1L, "MATERIAL", new BigDecimal("1000"));

        assertThat(warnResult.getStatus()).isEqualTo(BudgetCheckResult.Status.WARN);
    }

    @Test
    @DisplayName("checkBudget - 执行率低于阈值：PASS")
    void checkBudget_belowThreshold_pass() {
        stubEffectiveConfig("BLOCK", 80);
        when(budgetDetailMapper.sumBudgetByProjectAndCategory(1L, "LABOR"))
                .thenReturn(new BigDecimal("10000"));
        when(budgetOccupiedMapper.sumContractAmountForLabor(1L)).thenReturn(new BigDecimal("1000"));
        when(budgetOccupiedMapper.sumApprovedPaymentByCategory(1L, "LABOR")).thenReturn(null);

        // (1000 + 500) / 10000 = 15% < 80%
        BudgetCheckResult result = service.checkBudget(1L, "LABOR", new BigDecimal("500"));

        assertThat(result.getStatus()).isEqualTo(BudgetCheckResult.Status.PASS);
    }

    @Test
    @DisplayName("checkBudget - 执行率达阈值未超 100：WARN（预算不足时）或 PASS")
    void checkBudget_inWarningZone() {
        stubEffectiveConfig("WARN_ONLY", 80);
        when(budgetDetailMapper.sumBudgetByProjectAndCategory(1L, "MACHINE"))
                .thenReturn(new BigDecimal("10000"));
        when(budgetOccupiedMapper.sumContractAmountForMachine(1L)).thenReturn(new BigDecimal("7000"));
        when(budgetOccupiedMapper.sumApprovedPaymentByCategory(1L, "MACHINE")).thenReturn(new BigDecimal("1500"));

        // (7000+1500+1000)/10000 = 95% ≥ 80 且 < 100 → 预警后仍 PASS（WARN_ONLY 下未超支）
        BudgetCheckResult result = service.checkBudget(1L, "MACHINE", new BigDecimal("1000"));

        assertThat(result.getStatus()).isEqualTo(BudgetCheckResult.Status.PASS);
    }

    @Test
    @DisplayName("checkBudget - SUBCONTRACT 科目映射并超支 BLOCK")
    void checkBudget_subcontractOverBudget_blocks() {
        stubEffectiveConfig("BLOCK", 80);
        when(budgetDetailMapper.sumBudgetByProjectAndCategory(1L, "SUBCONTRACT"))
                .thenReturn(new BigDecimal("10000"));
        when(budgetOccupiedMapper.sumContractAmountForSubcontract(1L)).thenReturn(new BigDecimal("9500"));
        when(budgetOccupiedMapper.sumApprovedPaymentByCategory(1L, "SUBCONTRACT")).thenReturn(null);

        // (9500+1000)/10000 = 105% > 100 → BLOCK
        BudgetCheckResult result = service.checkBudget(1L, "SUBCONTRACT", new BigDecimal("1000"));

        assertThat(result.getStatus()).isEqualTo(BudgetCheckResult.Status.BLOCK);
        assertThat(result.getMessage()).contains("已超预算");
    }

    @Test
    @DisplayName("checkBudget - MATERIAL 科目：合同额 + 调拨净占用合计")
    void checkBudget_material_includesTransferNet() {
        stubEffectiveConfig("BLOCK", 80);
        when(budgetDetailMapper.sumBudgetByProjectAndCategory(1L, "MATERIAL"))
                .thenReturn(new BigDecimal("20000"));
        when(budgetOccupiedMapper.sumContractAmountForMaterial(1L)).thenReturn(new BigDecimal("5000"));
        when(budgetOccupiedMapper.sumTransferNetForMaterial(1L)).thenReturn(new BigDecimal("-1000")); // 调出净减
        when(budgetOccupiedMapper.sumApprovedPaymentByCategory(1L, "MATERIAL")).thenReturn(null);

        // (5000 + (-1000) + 2000)/20000 = 30% < 80% → PASS
        BudgetCheckResult result = service.checkBudget(1L, "MATERIAL", new BigDecimal("2000"));

        assertThat(result.getStatus()).isEqualTo(BudgetCheckResult.Status.PASS);
        verify(budgetOccupiedMapper).sumTransferNetForMaterial(1L);
    }

    @Test
    @DisplayName("checkBudget - 未知科目已发生额按 0 计")
    void checkBudget_unknownCategory_zeroUsed() {
        stubEffectiveConfig("BLOCK", 80);
        when(budgetDetailMapper.sumBudgetByProjectAndCategory(1L, "OTHER"))
                .thenReturn(new BigDecimal("1000"));
        when(budgetOccupiedMapper.sumApprovedPaymentByCategory(1L, "OTHER")).thenReturn(null);

        // (0 + 500)/1000 = 50% < 80 → PASS
        BudgetCheckResult result = service.checkBudget(1L, "OTHER", new BigDecimal("500"));

        assertThat(result.getStatus()).isEqualTo(BudgetCheckResult.Status.PASS);
    }

    // ── getEffectiveConfig 兜底 ──────────────────────────────────

    @Test
    @DisplayName("getEffectiveConfig - 查询异常时返回硬编码默认（BLOCK/80）")
    void getEffectiveConfig_exception_returnsHardCoded() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenThrow(new RuntimeException("DB 故障"));

        SysBudgetControlConfig config = service.getEffectiveConfig(1L);

        assertThat(config.getControlMode()).isEqualTo("BLOCK");
        assertThat(config.getWarningThreshold()).isEqualTo(80);
        assertThat(config.getIsDefault()).isEqualTo(1);
    }

    @Test
    @DisplayName("getEffectiveConfig - 无任何配置时返回硬编码默认")
    void getEffectiveConfig_noData_returnsHardCoded() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SysBudgetControlConfig config = service.getEffectiveConfig(null);

        assertThat(config.getControlMode()).isEqualTo("BLOCK");
    }

    // ── save/update 校验 ──────────────────────────────────

    @Test
    @DisplayName("save - 非法控制模式/阈值越界抛异常；合法时 isDefault=0 插入")
    void save_validates() {
        BudgetControlConfigDTO badMode = new BudgetControlConfigDTO();
        badMode.setControlMode("INVALID");
        badMode.setWarningThreshold(80);
        assertThatThrownBy(() -> service.save(badMode)).hasMessageContaining("控制模式无效");

        BudgetControlConfigDTO badThreshold = new BudgetControlConfigDTO();
        badThreshold.setControlMode("BLOCK");
        badThreshold.setWarningThreshold(49);
        assertThatThrownBy(() -> service.save(badThreshold)).hasMessageContaining("预警阈值必须在50-99之间");

        BudgetControlConfigDTO badThreshold2 = new BudgetControlConfigDTO();
        badThreshold2.setControlMode("BLOCK");
        badThreshold2.setWarningThreshold(null);
        assertThatThrownBy(() -> service.save(badThreshold2)).hasMessageContaining("预警阈值必须在50-99之间");

        BudgetControlConfigDTO ok = new BudgetControlConfigDTO();
        ok.setProjectId(1L);
        ok.setControlMode("WARN_ONLY");
        ok.setWarningThreshold(85);
        service.save(ok);
        verify(configMapper).insert(argThat(c ->
                c.getIsDefault() == 0 && "WARN_ONLY".equals(c.getControlMode())));
    }

    @Test
    @DisplayName("update - 不存在/非法入参抛异常；正常更新三字段")
    void update_validates() {
        when(configMapper.selectById(99L)).thenReturn(null);
        BudgetControlConfigDTO dto = new BudgetControlConfigDTO();
        dto.setControlMode("BLOCK");
        dto.setWarningThreshold(80);
        assertThatThrownBy(() -> service.update(99L, dto)).hasMessageContaining("预算控制配置不存在");

        SysBudgetControlConfig existing = new SysBudgetControlConfig();
        existing.setId(1L);
        when(configMapper.selectById(1L)).thenReturn(existing);
        BudgetControlConfigDTO badMode = new BudgetControlConfigDTO();
        badMode.setControlMode("NOPE");
        badMode.setWarningThreshold(80);
        assertThatThrownBy(() -> service.update(1L, badMode)).hasMessageContaining("控制模式无效");

        service.update(1L, dto);
        verify(configMapper).updateById(argThat(c ->
                "BLOCK".equals(c.getControlMode()) && c.getWarningThreshold() == 80));
    }

    // ── page 补充 ──────────────────────────────────

    @Test
    @DisplayName("page - 项目名筛选无匹配直接返回空")
    void page_projectNameNoMatch_returnsEmpty() {
        when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        assertThat(service.page(1, 10, "不存在的项目", null).getRecords()).isEmpty();
        verify(configMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("page - 项目名匹配后按 projectId 集合过滤")
    void page_projectNameMatched_filters() {
        BizProject p = new BizProject();
        p.setId(10L);
        when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(p));
        Page<SysBudgetControlConfig> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0L);
        when(configMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        assertThat(service.page(1, 10, "滨江", "BLOCK").getRecords()).isEmpty();
        verify(configMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }
}
