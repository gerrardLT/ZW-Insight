package com.zwinsight.basedata.aspect;

import com.zwinsight.basedata.annotation.BlacklistCheck;
import com.zwinsight.basedata.service.SupplierBlacklistService;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 供应商黑名单拦截切面
 * <p>
 * 拦截标注了 {@link BlacklistCheck} 注解的方法，在方法执行前自动校验
 * 供应商是否在黑名单中。如果供应商在黑名单中，则抛出 BusinessException 阻止操作。
 * </p>
 * <p>
 * 支持从方法参数中提取供应商ID，兼容以下字段命名：
 * <ul>
 *   <li>supplierId - 分包合同等实体</li>
 *   <li>partyBId - 采购合同等实体（乙方即供应商）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SupplierBlacklistAspect {

    private final SupplierBlacklistService blacklistService;

    @Before("@annotation(blacklistCheck)")
    public void checkBlacklist(JoinPoint joinPoint, BlacklistCheck blacklistCheck) {
        Long supplierId = extractSupplierId(joinPoint);
        if (supplierId == null) {
            log.debug("黑名单校验：无法从方法参数中提取供应商ID，跳过校验。方法: {}",
                    joinPoint.getSignature().toShortString());
            return;
        }

        if (blacklistService.isBlacklisted(supplierId)) {
            String reason = blacklistService.getBlacklistReason(supplierId);
            throw new BusinessException(
                    String.format("该供应商已被列入黑名单（原因：%s），禁止签约", reason));
        }
    }

    /**
     * 从方法参数中提取供应商ID
     * <p>
     * 按优先级尝试以下提取策略：
     * <ol>
     *   <li>遍历所有参数对象，尝试调用 getSupplierId() 方法</li>
     *   <li>遍历所有参数对象，尝试调用 getPartyBId() 方法</li>
     * </ol>
     * </p>
     */
    private Long extractSupplierId(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }

        // 优先尝试 getSupplierId()
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            Long id = invokeGetter(arg, "getSupplierId");
            if (id != null) {
                return id;
            }
        }

        // 其次尝试 getPartyBId()（采购合同中乙方即供应商）
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            Long id = invokeGetter(arg, "getPartyBId");
            if (id != null) {
                return id;
            }
        }

        return null;
    }

    /**
     * 通过反射调用对象的 getter 方法获取 Long 类型值
     */
    private Long invokeGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Long longValue) {
                return longValue;
            }
            if (value != null) {
                return Long.parseLong(value.toString());
            }
        } catch (NoSuchMethodException e) {
            // 对象没有该方法，正常跳过
        } catch (Exception e) {
            log.warn("黑名单校验：通过反射获取供应商ID失败，方法: {}", methodName, e);
        }
        return null;
    }
}
