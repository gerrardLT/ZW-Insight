package com.zwinsight.budget.aspect;

import com.zwinsight.budget.annotation.BudgetCheck;
import com.zwinsight.budget.context.BudgetWarningContext;
import com.zwinsight.budget.dto.BudgetCheckResult;
import com.zwinsight.budget.service.BudgetControlConfigService;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;

/**
 * 预算控制 AOP 切面
 * <p>
 * 拦截标注了 {@link BudgetCheck} 注解的业务方法，自动执行预算校验。
 * 根据 {@link BudgetCheckResult.Status} 执行不同策略：
 * <ul>
 *   <li>BLOCK — 抛出 BusinessException 阻止提交</li>
 *   <li>WARN — 将警告信息写入 BudgetWarningContext 线程变量</li>
 *   <li>PASS — 放行</li>
 * </ul>
 * 每次实时查询配置，满足 R6.7 变更后实时生效要求。
 * </p>
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetControlAspect {

    private final BudgetControlConfigService configService;

    @Before("@annotation(budgetCheck)")
    public void checkBudget(JoinPoint joinPoint, BudgetCheck budgetCheck) {
        Long projectId = extractProjectId(joinPoint);
        String costCategory = budgetCheck.category();
        BigDecimal amount = extractAmount(joinPoint);

        if (projectId == null) {
            log.warn("BudgetControlAspect: 无法从方法参数中提取 projectId，跳过预算校验。method={}",
                    joinPoint.getSignature().toShortString());
            return;
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("BudgetControlAspect: 金额为空或<=0，跳过预算校验。method={}",
                    joinPoint.getSignature().toShortString());
            return;
        }

        BudgetCheckResult result = configService.checkBudget(projectId, costCategory, amount);

        switch (result.getStatus()) {
            case BLOCK:
                throw new BusinessException(result.getMessage());
            case WARN:
                BudgetWarningContext.setWarning(result.getMessage());
                break;
            case PASS:
                break;
        }
    }

    /**
     * 从方法参数中提取 projectId
     * <p>
     * 查找参数中含有 getProjectId() 方法的对象并反射调用。
     * 若参数本身是 Long 类型且参数名暗示为 projectId，也会尝试匹配。
     * </p>
     */
    private Long extractProjectId(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            // 直接是 Long 类型，无法判定是否为 projectId，跳过
            // 优先通过对象的 getProjectId() 方法提取
            try {
                Method method = arg.getClass().getMethod("getProjectId");
                Object value = method.invoke(arg);
                if (value instanceof Long) {
                    return (Long) value;
                }
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
            } catch (NoSuchMethodException e) {
                // 该参数没有 getProjectId 方法，继续下一个
            } catch (Exception e) {
                log.warn("BudgetControlAspect: 反射调用 getProjectId() 异常", e);
            }
        }
        return null;
    }

    /**
     * 从方法参数中提取金额
     * <p>
     * 按以下优先级查找：
     * 1. 参数对象含有 getContractAmount() 方法
     * 2. 参数对象含有 getPaymentAmount() 方法
     * 3. 参数对象含有 getTotalAmount() 方法
     * 4. 参数本身是 BigDecimal 类型
     * </p>
     */
    private BigDecimal extractAmount(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();

        // 优先从对象的金额 getter 方法中提取
        String[] amountMethods = {"getContractAmount", "getPaymentAmount", "getTotalAmount"};
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            for (String methodName : amountMethods) {
                try {
                    Method method = arg.getClass().getMethod(methodName);
                    Object value = method.invoke(arg);
                    if (value instanceof BigDecimal) {
                        return (BigDecimal) value;
                    }
                } catch (NoSuchMethodException e) {
                    // 该参数没有该方法，尝试下一个
                } catch (Exception e) {
                    log.warn("BudgetControlAspect: 反射调用 {}() 异常", methodName, e);
                }
            }
        }

        // 最后尝试直接查找 BigDecimal 类型的参数
        for (Object arg : args) {
            if (arg instanceof BigDecimal) {
                return (BigDecimal) arg;
            }
        }

        return null;
    }
}
