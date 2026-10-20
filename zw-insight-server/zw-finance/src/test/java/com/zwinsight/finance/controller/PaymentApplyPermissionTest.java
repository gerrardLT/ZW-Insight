package com.zwinsight.finance.controller;

import com.zwinsight.common.dto.SubmitWithCcRequest;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.finance.domain.BizPaymentApply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V2026_124 付款申请权限矩阵钉住测试。
 *
 * <p>目的：项目经理通过细粒度码 finance:paymentapply:* 发起付款，但不能获得财务现金动作
 * （标记支付/撤销支付/批量操作）。方法级注解优先于类级注解，一旦有人误删/误改，
 * 本测试立即失败，避免权限面在无感知的情况下扩张。</p>
 */
class PaymentApplyPermissionTest {

    private List<String> methodPermissions(String method, Class<?>... parameterTypes) throws Exception {
        Method target = PaymentApplyController.class.getMethod(method, parameterTypes);
        RequiresPermission annotation = target.getAnnotation(RequiresPermission.class);
        assertThat(annotation)
                .as("方法 %s 必须显式声明 @RequiresPermission（方法级优先于类级）", method)
                .isNotNull();
        return Arrays.asList(annotation.value());
    }

    @Test
    @DisplayName("查询类方法同时保留 finance:view 与细粒度 view（财务与项目经理双通道）")
    void queryMethods_allowFinanceViewOrPaymentApplyView() throws Exception {
        assertThat(methodPermissions("page", int.class, int.class, Long.class, Long.class, String.class, String.class))
                .contains("finance:view", "finance:paymentapply:view");
        assertThat(methodPermissions("getById", Long.class))
                .contains("finance:view", "finance:paymentapply:view");
    }

    @Test
    @DisplayName("写入类方法按动作拆分细粒度码，同时保留 finance:view 兼容财务角色")
    void writeMethods_useGranularCodes() throws Exception {
        assertThat(methodPermissions("save", BizPaymentApply.class))
                .contains("finance:view", "finance:paymentapply:add");
        assertThat(methodPermissions("update", Long.class, BizPaymentApply.class))
                .contains("finance:view", "finance:paymentapply:edit");
        assertThat(methodPermissions("delete", Long.class))
                .contains("finance:view", "finance:paymentapply:delete");
        assertThat(methodPermissions("submit", Long.class, SubmitWithCcRequest.class))
                .contains("finance:payment:submit", "finance:paymentapply:submit");
    }

    @Test
    @DisplayName("现金动作不授予项目经理：标记支付/撤销支付/批量仍只认 finance:payment:submit")
    void cashActions_remainFinanceOnly() throws Exception {
        assertThat(methodPermissions("markPaid", Long.class, LocalDate.class, Long.class))
                .containsExactly("finance:payment:submit");
        assertThat(methodPermissions("revokePaid", Long.class))
                .containsExactly("finance:payment:submit");
        assertThat(methodPermissions("batch", com.zwinsight.finance.dto.BatchOperationRequest.class))
                .containsExactly("finance:payment:submit");
    }

    @Test
    @DisplayName("类级 finance:view 保留，作为未覆盖方法的兜底")
    void classLevelPermission_remainsFinanceView() {
        RequiresPermission classLevel = PaymentApplyController.class.getAnnotation(RequiresPermission.class);
        assertThat(classLevel).isNotNull();
        assertThat(classLevel.value()).containsExactly("finance:view");
    }
}
