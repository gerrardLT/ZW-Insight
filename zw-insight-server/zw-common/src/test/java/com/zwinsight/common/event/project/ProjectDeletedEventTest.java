package com.zwinsight.common.event.project;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目删除级联事件契约单元测试（R7-02）。
 * <p>
 * 本事件是 11 个模块级联清理监听器的唯一契约载体，字段语义一旦漂移，
 * 消费方会静默清理错项目或漏清租户，故把「构造参数完整保留」钉成测试。
 * </p>
 * <p>
 * 同时是 zw-common 覆盖率门禁的必需项：本类新增 9 行可执行代码
 * （构造 5 行 + 4 个 getter），若不覆盖会把模块行覆盖率从 532‰ 压到 527‰，
 * 低于 pom 中 {@code jacoco.min.coverage=0.530} 的门禁导致 CI 失败
 * （2026-09-18 实测踩坑：zw-common 一挂，Maven fail-fast 使其后 22 个模块全部 SKIPPED）。
 * </p>
 */
class ProjectDeletedEventTest {

    @Test
    @DisplayName("构造参数完整保留（消费方据 projectId 定位、据 tenantId 隔离、据 code/name 记日志）")
    void constructor_retainsAllFields() {
        Object source = new Object();
        ProjectDeletedEvent event = new ProjectDeletedEvent(source,
                90001L, 1L, "PRJ20260101001", "滨江花园一期工程");

        assertThat(event.getSource()).isSameAs(source);
        assertThat(event.getProjectId()).isEqualTo(90001L);
        assertThat(event.getTenantId()).isEqualTo(1L);
        assertThat(event.getProjectCode()).isEqualTo("PRJ20260101001");
        assertThat(event.getProjectName()).isEqualTo("滨江花园一期工程");
    }

    @Test
    @DisplayName("描述性字段可为 null：契约要求新增字段可空，不破坏旧消费者")
    void constructor_allowsNullDescriptiveFields() {
        ProjectDeletedEvent event = new ProjectDeletedEvent(this, 90002L, 1L, null, null);

        // 关键字段：projectId/tenantId 是清理定位与租户隔离的依据，不得为空
        assertThat(event.getProjectId()).isEqualTo(90002L);
        assertThat(event.getTenantId()).isEqualTo(1L);
        // 仅日志用途的字段允许 null
        assertThat(event.getProjectCode()).isNull();
        assertThat(event.getProjectName()).isNull();
    }

    @Test
    @DisplayName("是 ApplicationEvent 子类，可被 @EventListener 同步接收")
    void isApplicationEvent() {
        ProjectDeletedEvent event = new ProjectDeletedEvent(this, 1L, 1L, "C", "N");

        assertThat(event).isInstanceOf(ApplicationEvent.class);
    }
}
