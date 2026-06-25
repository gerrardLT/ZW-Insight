package com.zwinsight.system.datapermission;

import com.zwinsight.common.datapermission.DataScopeEnum;
import com.zwinsight.system.domain.dto.DataScopeUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import org.assertj.core.api.Assertions;

import java.util.Set;

// Feature: p0-data-permission-overdue, Property 1: DataScope 枚举值校验
/**
 * Property 1: DataScope 枚举值校验
 * <p>
 * 验证：对于任意字符串 s，将其作为 dataScope 保存时，
 * 当且仅当 s 属于 {ALL, DEPT_AND_CHILDREN, DEPT, PROJECT, SELF} 之一时保存成功；
 * 否则系统应拒绝并返回校验错误。
 * </p>
 * <p>
 * 同时验证 DataScopeEnum.fromName 的行为：
 * - 有效值返回对应枚举
 * - 无效值返回 SELF（默认兜底）
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
     * 验证：当输入属于有效值集合时，DataScopeEnum.fromName 返回对应的有效枚举
     */
    @Property(tries = 100)
    void validScopeString_fromName_returnsCorrespondingEnum(@ForAll("validScopes") String scopeName) {
        DataScopeEnum result = DataScopeEnum.fromName(scopeName);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.name()).isEqualToIgnoringCase(scopeName);
    }

    /**
     * 验证：当输入不属于有效值集合（且不是有效值的大小写变体）时，
     * DataScopeEnum.fromName 返回 SELF（兜底）
     */
    @Property(tries = 100)
    void invalidScopeString_fromName_returnsSelfAsDefault(@ForAll("trulyInvalidScopes") String scopeName) {
        DataScopeEnum result = DataScopeEnum.fromName(scopeName);

        // fromName 对无效输入返回 SELF（兜底机制）
        Assertions.assertThat(result).isEqualTo(DataScopeEnum.SELF);
    }

    /**
     * 验证：使用 Jakarta Validation，合法的 dataScope 值通过 DTO 校验
     */
    @Property(tries = 100)
    void validDataScope_passesValidation(@ForAll("validScopes") String dataScope) {
        DataScopeUpdateRequest request = new DataScopeUpdateRequest();
        request.setDataScope(dataScope);

        Set<ConstraintViolation<DataScopeUpdateRequest>> violations = validator.validate(request);

        Assertions.assertThat(violations).isEmpty();
    }

    /**
     * 验证：使用 Jakarta Validation，非法的 dataScope 值导致 DTO 校验失败
     */
    @Property(tries = 100)
    void invalidDataScope_failsValidation(@ForAll("invalidScopes") String dataScope) {
        DataScopeUpdateRequest request = new DataScopeUpdateRequest();
        request.setDataScope(dataScope);

        Set<ConstraintViolation<DataScopeUpdateRequest>> violations = validator.validate(request);

        Assertions.assertThat(violations).isNotEmpty();
    }

    /**
     * 验证：随机字符串中只有 {ALL, DEPT_AND_CHILDREN, DEPT, PROJECT, SELF} 通过校验
     */
    @Property(tries = 200)
    void randomString_onlyValidScopesPassValidation(@ForAll String input) {
        DataScopeUpdateRequest request = new DataScopeUpdateRequest();
        request.setDataScope(input);

        Set<ConstraintViolation<DataScopeUpdateRequest>> violations = validator.validate(request);

        if (VALID_DATA_SCOPES.contains(input)) {
            Assertions.assertThat(violations).isEmpty();
        } else {
            Assertions.assertThat(violations).isNotEmpty();
        }
    }

    /**
     * 验证：DataScopeEnum 的所有枚举值恰好等于 VALID_DATA_SCOPES 集合
     */
    @Property(tries = 100)
    void enumValues_matchValidSet(@ForAll DataScopeEnum scope) {
        Assertions.assertThat(VALID_DATA_SCOPES).contains(scope.name());
    }

    /**
     * 验证：null/空白字符串 fromName 返回 SELF
     */
    @Property(tries = 100)
    void blankOrNull_fromName_returnsSelf(@ForAll("blankStrings") String input) {
        DataScopeEnum result = DataScopeEnum.fromName(input);
        Assertions.assertThat(result).isEqualTo(DataScopeEnum.SELF);
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<String> validScopes() {
        return Arbitraries.of("ALL", "DEPT_AND_CHILDREN", "DEPT", "PROJECT", "SELF");
    }

    @Provide
    Arbitrary<String> invalidScopes() {
        return Arbitraries.of(
                "NONE", "ADMIN", "SUPER",
                "DEPARTMENT", "CHILDREN", "USER", "ROLE", "TENANT",
                "ALL_DATA", "MY_DEPT", "UNKNOWN", "INVALID",
                "ALLX", "XALL", "DEPTX", "PROJECTX", "SELFX",
                "READ", "WRITE", "EXECUTE", "MANAGE",
                "all", "dept", "self", "project", "dept_and_children"
        );
    }

    /**
     * 生成完全不匹配任何 DataScopeEnum（包括大小写变体）的字符串。
     * fromName 是忽略大小写的，所以 "all" → ALL。
     * 这里只生成真正无法匹配的字符串。
     */
    @Provide
    Arbitrary<String> trulyInvalidScopes() {
        return Arbitraries.of(
                "NONE", "ADMIN", "SUPER",
                "DEPARTMENT", "CHILDREN", "USER", "ROLE", "TENANT",
                "ALL_DATA", "MY_DEPT", "UNKNOWN", "INVALID",
                "ALLX", "XALL", "DEPTX", "PROJECTX", "SELFX",
                "READ", "WRITE", "EXECUTE", "MANAGE",
                "SCOPE_ALL", "DATA_SCOPE", "PERMISSION"
        );
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", "\n", "  \t  ");
    }
}
