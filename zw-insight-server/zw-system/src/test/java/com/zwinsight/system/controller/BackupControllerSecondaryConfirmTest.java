package com.zwinsight.system.controller;

import com.zwinsight.security.annotation.SecondaryConfirm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BackupController restore 端点二次确认守卫钉住测试。
 *
 * <p>背景（R6-01 受阻台账销项，2026-08-24）：restore 为覆盖全库的高风险操作，
 * 此前仅前端 ElMessageBox 确认、无服务端守卫，API 直调即可执行。现补
 * {@link SecondaryConfirm} 注解（切面行为由 zw-security Property 20 钉住：
 * 缺失 X-Confirm-Password → 449；密码错误 → 403；15 分钟 5 次 → 423）。
 * 本测试以反射钉住注解与端点映射，防重构丢失。</p>
 */
class BackupControllerSecondaryConfirmTest {

    @Test
    @DisplayName("正常路径：restore 方法标注 @SecondaryConfirm 且提示文案含高风险说明")
    void testRestore_hasSecondaryConfirmAnnotation() throws NoSuchMethodException {
        Method restore = BackupController.class.getMethod("restore", Long.class);
        SecondaryConfirm ann = restore.getAnnotation(SecondaryConfirm.class);
        assertThat(ann)
                .as("restore 为覆盖全库的高风险操作，必须要求登录密码二次确认（R6-01）")
                .isNotNull();
        assertThat(ann.message()).contains("高风险").contains("登录密码");
    }

    @Test
    @DisplayName("守卫路径：restore 端点映射保持 POST /restore/{id}，防重构丢端点或换方法")
    void testRestore_endpointMappingPreserved() throws NoSuchMethodException {
        Method restore = BackupController.class.getMethod("restore", Long.class);
        PostMapping mapping = restore.getAnnotation(PostMapping.class);
        assertThat(mapping).as("restore 应为 POST 端点").isNotNull();
        assertThat(mapping.value()).containsExactly("/restore/{id}");
    }

    @Test
    @DisplayName("边界路径：非高风险的备份执行/查询/下载端点不要求二次确认（避免过度拦截）")
    void testNonCriticalEndpoints_noSecondaryConfirm() throws NoSuchMethodException {
        Method execute = BackupController.class.getMethod("execute");
        assertThat(execute.getAnnotation(SecondaryConfirm.class))
                .as("execute 为备份动作，不覆盖数据，无需密码二次确认")
                .isNull();
    }
}
