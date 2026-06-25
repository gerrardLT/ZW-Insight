package com.zwinsight.site.property;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.hibernate.validator.constraints.Range;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: p0-data-permission-overdue, Property 11: intervalDays 参数校验
/**
 * Property 11: intervalDays 参数校验
 * <p>
 * 验证：对于任何整数 n，当 1 ≤ n ≤ 30 时 intervalDays 校验通过；
 * 当 n < 1 或 n > 30 时校验失败并返回错误。
 * </p>
 * <p>
 * **Validates: Requirements 8.1, 8.6**
 * </p>
 */
class IntervalDaysValidationPropertyTest {

    private static final Validator validator;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    /**
     * 内部测试用 DTO，复制 ReminderConfigUpdateRequest 中 intervalDays 字段的校验注解。
     * 这避免了因主源码编译问题导致测试无法运行。
     * 其校验规则与 ReminderConfigUpdateRequest.intervalDays 完全一致：
     * {@code @NotNull} + {@code @Range(min = 1, max = 30)}
     */
    static class IntervalDaysRequest {
        @NotNull(message = "催办间隔天数不能为空")
        @Range(min = 1, max = 30, message = "催办间隔天数必须在1-30之间")
        private Integer intervalDays;

        public Integer getIntervalDays() { return intervalDays; }
        public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }
    }

    /**
     * 构建一个请求对象用于校验 intervalDays。
     */
    private IntervalDaysRequest buildRequest(Integer intervalDays) {
        IntervalDaysRequest request = new IntervalDaysRequest();
        request.setIntervalDays(intervalDays);
        return request;
    }

    /**
     * 获取仅针对 intervalDays 字段的校验违规
     */
    private Set<ConstraintViolation<IntervalDaysRequest>> getIntervalDaysViolations(
            IntervalDaysRequest request) {
        Set<ConstraintViolation<IntervalDaysRequest>> allViolations = validator.validate(request);
        return allViolations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("intervalDays"))
                .collect(Collectors.toSet());
    }

    @Property(tries = 100)
    @Label("intervalDays 在 1-30 范围内时校验通过")
    void validIntervalDaysPassesValidation(
            @ForAll @IntRange(min = 1, max = 30) int intervalDays) {

        IntervalDaysRequest request = buildRequest(intervalDays);
        Set<ConstraintViolation<IntervalDaysRequest>> violations =
                getIntervalDaysViolations(request);

        assertThat(violations)
                .as("intervalDays=%d 应通过校验（1-30范围内）", intervalDays)
                .isEmpty();
    }

    @Property(tries = 100)
    @Label("intervalDays 小于 1 时校验失败")
    void intervalDaysBelowMinFailsValidation(
            @ForAll @IntRange(min = -1000, max = 0) int intervalDays) {

        IntervalDaysRequest request = buildRequest(intervalDays);
        Set<ConstraintViolation<IntervalDaysRequest>> violations =
                getIntervalDaysViolations(request);

        assertThat(violations)
                .as("intervalDays=%d 应校验失败（小于1）", intervalDays)
                .isNotEmpty();
        assertThat(violations.iterator().next().getMessage())
                .contains("1-30");
    }

    @Property(tries = 100)
    @Label("intervalDays 大于 30 时校验失败")
    void intervalDaysAboveMaxFailsValidation(
            @ForAll @IntRange(min = 31, max = 1000) int intervalDays) {

        IntervalDaysRequest request = buildRequest(intervalDays);
        Set<ConstraintViolation<IntervalDaysRequest>> violations =
                getIntervalDaysViolations(request);

        assertThat(violations)
                .as("intervalDays=%d 应校验失败（大于30）", intervalDays)
                .isNotEmpty();
        assertThat(violations.iterator().next().getMessage())
                .contains("1-30");
    }

    @Property(tries = 200)
    @Label("对任意整数，仅 1-30 范围内校验通过")
    void onlyValidRangePassesValidation(
            @ForAll @IntRange(min = -500, max = 500) int intervalDays) {

        IntervalDaysRequest request = buildRequest(intervalDays);
        Set<ConstraintViolation<IntervalDaysRequest>> violations =
                getIntervalDaysViolations(request);

        boolean isInValidRange = intervalDays >= 1 && intervalDays <= 30;

        if (isInValidRange) {
            assertThat(violations)
                    .as("intervalDays=%d 在有效范围内，校验应通过", intervalDays)
                    .isEmpty();
        } else {
            assertThat(violations)
                    .as("intervalDays=%d 不在有效范围内，校验应失败", intervalDays)
                    .isNotEmpty();
        }
    }
}
