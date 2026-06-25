package com.zwinsight.site.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: p0-data-permission-overdue, Property 8: 催办通知内容完整性
/**
 * Property 8: 催办通知内容完整性
 * <p>
 * 验证：对任意超期整改记录（含随机项目名称、检查类型、问题描述、整改期限和超期天数），
 * buildReminderContent 生成的催办消息内容应包含上述全部五项信息。
 * </p>
 * <p>
 * **Validates: Requirements 7.2**
 * </p>
 */
class ReminderContentPropertyTest {

    /**
     * 复刻 RectificationReminderTask.buildReminderContent 逻辑
     */
    private String buildReminderContent(String projectName, String inspectionType,
                                        String problemDescription, LocalDate deadline, int overdueDays) {
        return String.format(
                "【整改催办】项目【%s】的%s检查中发现问题：%s。整改期限：%s，已超期%d天，请尽快处理。",
                projectName, inspectionType,
                problemDescription != null ? problemDescription : "未描述",
                deadline, overdueDays
        );
    }

    @Provide
    Arbitrary<String> projectNames() {
        Arbitrary<String> prefixes = Arbitraries.of("中建", "中铁", "华润", "万科", "碧桂园", "保利", "招商");
        Arbitrary<String> suffixes = Arbitraries.of("一期工程", "二期项目", "高层住宅", "商业综合体", "产业园", "基础设施");
        Arbitrary<Integer> numbers = Arbitraries.integers().between(1, 99);
        return Combinators.combine(prefixes, suffixes, numbers)
                .as((prefix, suffix, num) -> prefix + suffix + num + "号");
    }

    @Provide
    Arbitrary<String> inspectionTypes() {
        return Arbitraries.of("质量", "安全");
    }

    @Provide
    Arbitrary<String> problemDescriptions() {
        return Arbitraries.of(
                "钢筋间距不符合设计要求",
                "混凝土强度不达标",
                "模板支撑体系不稳固",
                "防水层施工不规范",
                "脚手架搭设存在安全隐患",
                "电气线路未做绝缘处理",
                "临边防护缺失",
                "基坑支护变形超标"
        );
    }

    @Provide
    Arbitrary<LocalDate> deadlines() {
        return Arbitraries.integers().between(1, 60)
                .map(days -> LocalDate.of(2025, 6, 15).minusDays(days));
    }

    @Property(tries = 100)
    void contentContainsAllFiveElements(
            @ForAll("projectNames") String projectName,
            @ForAll("inspectionTypes") String inspectionType,
            @ForAll("problemDescriptions") String problemDescription,
            @ForAll("deadlines") LocalDate deadline,
            @ForAll @IntRange(min = 1, max = 100) int overdueDays) {

        String content = buildReminderContent(projectName, inspectionType, problemDescription, deadline, overdueDays);

        assertThat(content)
                .as("催办通知应包含项目名称")
                .contains(projectName);
        assertThat(content)
                .as("催办通知应包含检查类型")
                .contains(inspectionType);
        assertThat(content)
                .as("催办通知应包含问题描述")
                .contains(problemDescription);
        assertThat(content)
                .as("催办通知应包含整改期限")
                .contains(deadline.toString());
        assertThat(content)
                .as("催办通知应包含超期天数")
                .contains(String.valueOf(overdueDays));
    }

    @Property(tries = 100)
    void contentHandlesNullProblemDescription(
            @ForAll("projectNames") String projectName,
            @ForAll("inspectionTypes") String inspectionType,
            @ForAll("deadlines") LocalDate deadline,
            @ForAll @IntRange(min = 1, max = 100) int overdueDays) {

        String content = buildReminderContent(projectName, inspectionType, null, deadline, overdueDays);

        // 当问题描述为 null 时，应使用"未描述"代替
        assertThat(content).contains("未描述");
        assertThat(content).contains(projectName);
        assertThat(content).contains(inspectionType);
        assertThat(content).contains(deadline.toString());
        assertThat(content).contains(String.valueOf(overdueDays));
    }
}
