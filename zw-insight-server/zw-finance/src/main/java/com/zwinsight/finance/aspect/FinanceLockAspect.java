package com.zwinsight.finance.aspect;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.annotation.FinanceLockCheck;
import com.zwinsight.finance.service.FinanceLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 财务封账 AOP 切面
 * <p>
 * 拦截标注了 {@link FinanceLockCheck} 注解的财务写入方法（新增、编辑、删除）。
 * 通过反射从方法参数对象提取业务日期字段，解析为封账期间（YYYY-MM），
 * 优先从 Redis 查询封账状态，Redis 不可用时降级到数据库查询；
 * 若命中 LOCKED 状态则抛出 {@link BusinessException} 阻止操作。
 * </p>
 *
 * <ul>
 *   <li>业务日期为空 → 抛出 "业务日期不可为空"</li>
 *   <li>Redis 与 DB 均不可用 → 抛出 "系统暂时无法校验封账状态，请稍后重试"</li>
 *   <li>命中已封账期间 → 抛出 "期间{period}已封账，禁止{operation}"</li>
 * </ul>
 *
 * @see FinanceLockCheck
 * @see FinanceLockService
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class FinanceLockAspect {

    private final FinanceLockService financeLockService;

    /** 封账状态：已封账 */
    private static final String STATUS_LOCKED = "LOCKED";
    /** 期间格式 YYYY-MM */
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Before("@annotation(financeLockCheck)")
    public void checkFinanceLock(JoinPoint joinPoint, FinanceLockCheck financeLockCheck) {
        String dateField = financeLockCheck.dateField();
        String operation = financeLockCheck.operation();

        // 1. 通过反射从参数对象提取业务日期
        YearMonth bizPeriod = extractBizPeriod(joinPoint, dateField);
        if (bizPeriod == null) {
            throw new BusinessException(400, "业务日期不可为空");
        }

        String period = bizPeriod.format(PERIOD_FORMATTER);

        // 2. 查询封账状态（Redis 优先，DB 降级；均不可用则抛异常）
        String status;
        try {
            status = financeLockService.getStatus(period);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("FinanceLockAspect: 封账状态查询失败（缓存与数据库均不可用），period={}", period, e);
            throw new BusinessException(503, "系统暂时无法校验封账状态，请稍后重试", e);
        }

        // 3. 命中 LOCKED 状态时拦截
        if (STATUS_LOCKED.equals(status)) {
            throw new BusinessException(403, "期间" + period + "已封账，禁止" + operation);
        }
    }

    /**
     * 通过反射从方法参数对象中提取业务日期，并解析为 {@link YearMonth}。
     * <p>
     * 字段名 {@code dateField} 通过驼峰 getter 规则转换为 getter 方法名
     * （如 {@code applyDate} → {@code getApplyDate}），依次在各参数对象上尝试调用。
     * 支持 {@link LocalDate}、{@link LocalDateTime}、{@link Date} 以及日期字符串。
     * </p>
     *
     * @return 解析得到的业务期间；若任何参数均无法提取到非空日期则返回 {@code null}
     */
    private YearMonth extractBizPeriod(JoinPoint joinPoint, String dateField) {
        if (dateField == null || dateField.isBlank()) {
            return null;
        }
        String getterName = toGetterName(dateField);
        Object[] args = joinPoint.getArgs();

        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            // 跳过基础类型/日期类型本身，仅从业务对象的 getter 提取
            Method getter;
            try {
                getter = arg.getClass().getMethod(getterName);
            } catch (NoSuchMethodException e) {
                // 该参数没有对应字段的 getter，尝试下一个参数
                continue;
            }
            try {
                Object value = getter.invoke(arg);
                YearMonth ym = toYearMonth(value);
                if (ym != null) {
                    return ym;
                }
            } catch (Exception e) {
                log.warn("FinanceLockAspect: 反射调用 {}() 异常", getterName, e);
            }
        }
        return null;
    }

    /**
     * 将字段名转换为标准 getter 方法名，如 {@code applyDate} → {@code getApplyDate}。
     */
    private String toGetterName(String fieldName) {
        return "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    /**
     * 将日期值转换为 {@link YearMonth}。支持 LocalDate / LocalDateTime / Date / 字符串。
     *
     * @return 转换结果；无法识别或为空时返回 {@code null}
     */
    private YearMonth toYearMonth(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return YearMonth.from(localDate);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return YearMonth.from(localDateTime);
        }
        if (value instanceof Date date) {
            return YearMonth.from(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }
        if (value instanceof CharSequence cs) {
            String text = cs.toString().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                // 优先按完整日期解析（yyyy-MM-dd[ ...]），再回退按 yyyy-MM 解析
                return YearMonth.from(LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text));
            } catch (Exception ignored) {
                try {
                    return YearMonth.parse(text, PERIOD_FORMATTER);
                } catch (Exception e) {
                    log.warn("FinanceLockAspect: 业务日期字符串无法解析，value={}", text);
                    return null;
                }
            }
        }
        log.warn("FinanceLockAspect: 不支持的业务日期类型: {}", value.getClass().getName());
        return null;
    }
}
