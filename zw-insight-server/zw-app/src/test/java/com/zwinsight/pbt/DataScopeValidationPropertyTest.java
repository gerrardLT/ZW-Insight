package com.zwinsight.pbt;

import com.zwinsight.common.datapermission.DataScopeEnum;
import com.zwinsight.system.domain.dto.DataScopeUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import org.assertj.core.api.Assertions;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Feature: p0-data-permission-overdue, Property 1: DataScope 枚举值校验
/**
 * Property 1: DataScope 枚举值校验
 * <p>
 * 验证：对于任意字符串 s，将其作为 dataScope 保存时，
 * 当且仅当 s 属于 {ALL, DEPT_AND_CHILDREN, DEPT, PROJECT, SELF} 之一时保存成功；
 * 否则系统应拒绝并返回校验错误。
 * </p>
 * <p>
 * **Validates: Requirements 1.1, 1.5**
 * </p>
 */
@Tag("Feature: p0-data-permission-overdue, Property 1: DataScope 枚举值校验")
class DataScopeValidationPropertyTest {

    private static final Validator validator;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /** 合法的 DataScope 值集合 */
    private static final Set<String> VALID_DATA_SCOPES = Set.of(
            "ALL", "DEPT_AND_CHILDREN", "DEPT", "PROJECT", "SELF"
    );

    /**
     * 验证所有合法的 DataScopeEnum 值通过校验
     */
    @Property(tries = 100)
    void validDataScope_passesValidation(@ForAll("validScopes") String dataScope) {
        DataScopeUpdateRequest request = new DataScopeUpdateRequest();
        request.setDataScope(dataScope);

        Set<ConstraintViolation<DataScopeUpdateRequest>> violations = validator.validate(request);

        Assertions.assertThat(violations).isEmpty();
    }

    /**
     * 验证所有非法字符串作为 dataScope 时校验失败
     */
    @Property(tries = 100)
    void invalidDataScope_failsValidation(@ForAll("invalidScopes") String dataScope) {
        DataScopeUpdateRequest request = new DataScopeUpdateRequest();
        request.setDataScope(dataScope);

        Set<ConstraintViolation<DataScopeUpdateRequest>> violations = validator.validate(request);

        Assertions.assertThat(violations).isNotEmpty();
        // 确认是 pattern 校验失败
        boolean hasPatternViolation = violations.stream()
                .anyMatch(v -> v.getMessage().contains("数据范围必须为"));
        Assertions.assertThat(hasPatternViolation).isTrue();
    }

    /**
     * 验证空字符串不通过校验（@NotBlank）
     */
    @Property(tries = 100)
    void blankDataScope_failsValidation(@ForAll("blankStrings") String dataScope) {
        DataScopeUpdateRequest request = new DataScopeUpdateRequest();
        request.setDataScope(dataScope);

        Set<ConstraintViolation<DataScopeUpdateRequest>> violations = validator.validate(request);

        Assertions.assertThat(violations).isNotEmpty();
    }

    /**
     * 验证 null 值不通过校验
     */
    @Property(tries = 100)
    void nullDataScope_failsValidation(@ForAll("userIds") Long userId) {
        DataScopeUpdateRequest request = new DataScopeUpdateRequest();
        request.setDataScope(null);

        Set<ConstraintViolation<DataScopeUpdateRequest>> violations = validator.validate(request);

        Assertions.assertThat(violations).isNotEmpty();
    }

    /**
     * 验证 DataScopeEnum 和 VALID_DATA_SCOPES 集合完全一致
     * （确保枚举不会新增值而不更新校验正则）
     */
    @Property(tries = 100)
    void enumValues_matchValidSet(@ForAll DataScopeEnum scope) {
        Assertions.assertThat(VALID_DATA_SCOPES).contains(scope.name());
    }

    /**
     * 验证大小写敏感 — 小写的合法值不通过校验
     */
    @Property(tries = 100)
    void lowercaseValidScope_failsValidation(@ForAll("lowercaseValidScopes") String dataScope) {
        DataScopeUpdateRequest request = new DataScopeUpdateRequest();
        request.setDataScope(dataScope);

        Set<ConstraintViolation<DataScopeUpdateRequest>> violations = validator.validate(request);

        Assertions.assertThat(violations).isNotEmpty();
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1L, 999999L);
    }

    /**
     * 从合法值集合中随机取值
     */
    @Provide
    Arbitrary<String> validScopes() {
        return Arbitraries.of("ALL", "DEPT_AND_CHILDREN", "DEPT", "PROJECT", "SELF");
    }

    /**
     * 生成不在合法集合中的随机字符串
     */
    @Provide
    Arbitrary<String> invalidScopes() {
        return Arbitraries.of(
                "NONE", "ADMIN", "SUPER", "all", "dept", "self", "project",
                "DEPARTMENT", "CHILDREN", "USER", "ROLE", "TENANT",
                "ALL_DATA", "MY_DEPT", "UNKNOWN", "INVALID",
                "dept_and_children", "All", "Dept", "Self",
                "ALLX", "XALL", "DEPTX", "PROJECTX", "SELFX",
                "READ", "WRITE", "EXECUTE", "MANAGE"
        );
    }

    /**
     * 生成空白字符串
     */
    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", "\n", "  \t  ");
    }

    /**
     * 生成小写版本的合法值
     */
    @Provide
    Arbitrary<String> lowercaseValidScopes() {
        return Arbitraries.of("all", "dept_and_children", "dept", "project", "self");
    }
}
