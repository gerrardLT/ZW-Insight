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
}
