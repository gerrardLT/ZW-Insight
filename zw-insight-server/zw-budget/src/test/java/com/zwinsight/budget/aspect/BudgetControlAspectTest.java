package com.zwinsight.budget.aspect;

import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.budget.context.BudgetWarningContext;
import com.zwinsight.budget.dto.BudgetCheckResult;
import com.zwinsight.budget.service.BudgetControlConfigService;
import com.zwinsight.common.exception.BusinessException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BudgetControlAspect 单元测试（P1 BUD-ASP-04/05 补测，2026-08-13）
 * <p>
 * 覆盖降级分支：无 projectId 跳过校验（BUD-ASP-04）、金额 null/&lt;=0 跳过校验（BUD-ASP-05），
 * 以及 BLOCK/WARN/PASS 三种结果的策略分支。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetControlAspectTest {

    @Mock
    private BudgetControlConfigService configService;

    private BudgetControlAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new BudgetControlAspect(configService);
    }

    @AfterEach
    void tearDown() {
        // 防止 WARN 分支用例污染线程变量
        BudgetWarningContext.clear();
    }

    /** 同时携带 projectId 与金额的业务参数对象 */
    public static class BizArg {
        private final Long projectId;
        private final BigDecimal totalAmount;

        public BizArg(Long projectId, BigDecimal totalAmount) {
            this.projectId = projectId;
            this.totalAmount = totalAmount;
        }

        public Long getProjectId() {
            return projectId;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }
    }

    /** 无 getProjectId 方法的参数对象（降级场景） */
    public static class NoProjectArg {
        private final BigDecimal totalAmount;

        public NoProjectArg(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }
    }

    private JoinPoint joinPoint(Object... args) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("MockService.submit(..)");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    private BudgetCheck budgetCheck() {
        BudgetCheck check = mock(BudgetCheck.class);
        when(check.category()).thenReturn("MATERIAL");
        return check;
    }

    @Test
    @DisplayName("无 projectId 降级跳过校验（P1 BUD-ASP-04）")
    void noProjectId_skipsCheck() {
        aspect.checkBudget(joinPoint(new NoProjectArg(new BigDecimal("1000"))), budgetCheck());

        verifyNoInteractions(configService);
    }

    @Test
    @DisplayName("参数全为 null 降级跳过校验（P1 BUD-ASP-04）")
    void allNullArgs_skipsCheck() {
        aspect.checkBudget(joinPoint((Object) null), budgetCheck());

        verifyNoInteractions(configService);
    }

    @Test
    @DisplayName("金额为 null 降级跳过校验（P1 BUD-ASP-05）")
    void nullAmount_skipsCheck() {
        aspect.checkBudget(joinPoint(new BizArg(100L, null)), budgetCheck());

        verifyNoInteractions(configService);
    }

    @Test
    @DisplayName("金额 <=0 降级跳过校验（P1 BUD-ASP-05）")
    void nonPositiveAmount_skipsCheck() {
        aspect.checkBudget(joinPoint(new BizArg(100L, BigDecimal.ZERO)), budgetCheck());
        aspect.checkBudget(joinPoint(new BizArg(100L, new BigDecimal("-1"))), budgetCheck());

        verifyNoInteractions(configService);
    }

    @Test
    @DisplayName("BLOCK 结果抛 BusinessException 阻止提交")
    void block_throws() {
        when(configService.checkBudget(anyLong(), anyString(), any(BigDecimal.class)))
                .thenReturn(BudgetCheckResult.block("超出预算上限"));

        assertThatThrownBy(() ->
                aspect.checkBudget(joinPoint(new BizArg(100L, new BigDecimal("1000"))), budgetCheck()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出预算上限");
    }

    @Test
    @DisplayName("WARN 结果写入线程变量不阻断")
    void warn_setsContext() {
        when(configService.checkBudget(anyLong(), anyString(), any(BigDecimal.class)))
                .thenReturn(BudgetCheckResult.warn("预算执行率已达 95%"));

        assertThatCode(() ->
                aspect.checkBudget(joinPoint(new BizArg(100L, new BigDecimal("1000"))), budgetCheck()))
                .doesNotThrowAnyException();

        assertThat(BudgetWarningContext.getWarning()).isEqualTo("预算执行率已达 95%");
    }

    @Test
    @DisplayName("PASS 结果放行且不写线程变量")
    void pass_noSideEffect() {
        when(configService.checkBudget(anyLong(), anyString(), any(BigDecimal.class)))
                .thenReturn(BudgetCheckResult.pass());

        assertThatCode(() ->
                aspect.checkBudget(joinPoint(new BizArg(100L, new BigDecimal("1000"))), budgetCheck()))
                .doesNotThrowAnyException();

        assertThat(BudgetWarningContext.getWarning()).isNull();
    }

    // ==================== P2-M4 A2：空 category 自动提取与豁免 ====================

    /** 携带合同分类通道的业务参数（付款申请形态） */
    public static class ContractCategoryArg {
        public Long getProjectId() { return 100L; }
        public String getContractCategory() { return "PURCHASE"; }
        public BigDecimal getPaymentAmount() { return new BigDecimal("500"); }
    }

    /** 携带成本科目通道的业务参数 */
    public static class CostCategoryArg {
        public Long getProjectId() { return 101L; }
        public String getCostCategory() { return "LABOR"; }
        public BigDecimal getContractAmount() { return new BigDecimal("800"); }
    }

    /** 携带泛化 category 通道的业务参数 */
    public static class NameCategoryArg {
        public Long getProjectId() { return 102L; }
        public String getCategory() { return "MACHINE"; }
        public BigDecimal getTotalAmount() { return new BigDecimal("900"); }
    }

    /** 无任何科目通道的业务参数（其他付款形态） */
    public static class PlainAmountArg {
        public Long getProjectId() { return 103L; }
        public BigDecimal getPaymentAmount() { return new BigDecimal("700"); }
    }

    private BudgetCheck blankCategoryCheck() {
        BudgetCheck check = mock(BudgetCheck.class);
        when(check.category()).thenReturn("");
        return check;
    }

    @Test
    @DisplayName("空 category 经 getContractCategory 自动提取并按提取科目校验（P2-M4 A2）")
    void blankCategory_extractsFromContractCategory() {
        when(configService.checkBudget(100L, "PURCHASE", new BigDecimal("500")))
                .thenReturn(BudgetCheckResult.pass());

        aspect.checkBudget(joinPoint(new ContractCategoryArg()), blankCategoryCheck());

        verify(configService).checkBudget(100L, "PURCHASE", new BigDecimal("500"));
    }

    @Test
    @DisplayName("空 category 经 getCostCategory 与 getCategory 通道同样可提取")
    void blankCategory_extractsFromOtherChannels() {
        when(configService.checkBudget(anyLong(), anyString(), any(BigDecimal.class)))
                .thenReturn(BudgetCheckResult.pass());

        aspect.checkBudget(joinPoint(new CostCategoryArg()), blankCategoryCheck());
        aspect.checkBudget(joinPoint(new NameCategoryArg()), blankCategoryCheck());

        verify(configService).checkBudget(101L, "LABOR", new BigDecimal("800"));
        verify(configService).checkBudget(102L, "MACHINE", new BigDecimal("900"));
    }

    @Test
    @DisplayName("无科目通道的实体豁免校验——不再按空科目查预算误杀（A2 核心修复）")
    void blankCategory_noChannel_skipsCheck() {
        aspect.checkBudget(joinPoint(new PlainAmountArg()), blankCategoryCheck());

        verifyNoInteractions(configService);
    }

    @Test
    @DisplayName("空 category 且科目通道返回空白串同样豁免")
    void blankCategory_blankChannelValue_skipsCheck() {
        Object blankArg = new Object() {
            public Long getProjectId() { return 104L; }
            public String getCostCategory() { return "  "; }
            public BigDecimal getPaymentAmount() { return new BigDecimal("100"); }
        };

        aspect.checkBudget(joinPoint(blankArg), blankCategoryCheck());

        verifyNoInteractions(configService);
    }
}
