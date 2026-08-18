package com.zwinsight.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2eTestGuard 单元测试
 * <p>删除守卫对 E2E_TEST_ 前缀测试数据放行的判定逻辑（状态级守卫专用，引用守卫不受影响）。</p>
 */
class E2eTestGuardTest {

    /** 模拟业务实体（含名称字段与非 String 字段） */
    static class FakeEntity {
        private String contractName;
        private Long amount;
    }

    /** 模拟父类（验证继承字段扫描） */
    static class ChildEntity extends FakeNamedBase {
        private String status;
    }

    static class FakeNamedBase {
        String projectName;
    }

    @Test
    @DisplayName("isE2eTestData — null/空/普通文本均为 false")
    void isE2eTestData_negativeCases() {
        assertThat(E2eTestGuard.isE2eTestData(null)).isFalse();
        assertThat(E2eTestGuard.isE2eTestData("")).isFalse();
        assertThat(E2eTestGuard.isE2eTestData("滨江花园一期")).isFalse();
        assertThat(E2eTestGuard.isE2eTestData("e2e_test_小写前缀不命中")).isFalse();
        assertThat(E2eTestGuard.isE2eTestData("前缀在中间_E2E_TEST_")).isFalse();
    }

    @Test
    @DisplayName("isE2eTestData — E2E_TEST_ 前缀命中")
    void isE2eTestData_prefixMatches() {
        assertThat(E2eTestGuard.isE2eTestData("E2E_TEST_1723900000000_合同")).isTrue();
        assertThat(E2eTestGuard.isE2eTestData("E2E_TEST_")).isTrue();
    }

    @Test
    @DisplayName("containsE2eTestMarker — null 实体与无标记实体返回 false")
    void containsE2eTestMarker_negative() {
        assertThat(E2eTestGuard.containsE2eTestMarker(null)).isFalse();

        FakeEntity entity = new FakeEntity();
        entity.contractName = "真实合同名";
        entity.amount = 100L;
        assertThat(E2eTestGuard.containsE2eTestMarker(entity)).isFalse();
    }

    @Test
    @DisplayName("containsE2eTestMarker — 任一 String 字段带前缀即命中")
    void containsE2eTestMarker_anyStringField() {
        FakeEntity entity = new FakeEntity();
        entity.contractName = "E2E_TEST_1723900000000_采购合同";
        assertThat(E2eTestGuard.containsE2eTestMarker(entity)).isTrue();
    }

    @Test
    @DisplayName("containsE2eTestMarker — 扫描父类继承字段")
    void containsE2eTestMarker_inheritedField() {
        ChildEntity child = new ChildEntity();
        child.status = "APPROVED";
        child.projectName = "E2E_TEST_项目";
        assertThat(E2eTestGuard.containsE2eTestMarker(child)).isTrue();

        ChildEntity clean = new ChildEntity();
        clean.status = "APPROVED";
        clean.projectName = "滨江花园一期";
        assertThat(E2eTestGuard.containsE2eTestMarker(clean)).isFalse();
    }

    /** 模拟明细行（主表无命名字段时前缀落在明细） */
    static class FakeDetail {
        private String materialName;
    }

    @Test
    @DisplayName("containsE2eTestMarker(主表+明细) — 主表无标记、明细命中即放行")
    void containsE2eTestMarker_detailMarkerBypass() {
        FakeEntity master = new FakeEntity();
        master.contractName = "真实合同名";

        FakeDetail detail = new FakeDetail();
        detail.materialName = "E2E_TEST_1723900000000_钢筋";
        assertThat(E2eTestGuard.containsE2eTestMarker(master, java.util.List.of(detail))).isTrue();
    }

    @Test
    @DisplayName("containsE2eTestMarker(主表+明细) — 均无标记/明细 null 返回 false")
    void containsE2eTestMarker_detailNegative() {
        FakeEntity master = new FakeEntity();
        master.contractName = "真实合同名";

        FakeDetail detail = new FakeDetail();
        detail.materialName = "普通钢筋";
        assertThat(E2eTestGuard.containsE2eTestMarker(master, java.util.List.of(detail))).isFalse();
        assertThat(E2eTestGuard.containsE2eTestMarker(master, null)).isFalse();
        assertThat(E2eTestGuard.containsE2eTestMarker(null, java.util.List.of())).isFalse();
    }
}
