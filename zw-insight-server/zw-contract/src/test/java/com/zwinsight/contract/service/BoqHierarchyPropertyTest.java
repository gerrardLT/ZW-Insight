package com.zwinsight.contract.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property P1: BOQ 层级一致性
 * <p>
 * 验证 getParentCode 方法的属性：对于随机生成的 N 级编码（用 "." 分隔的数字），
 * getParentCode 返回值的层级总是比输入少 1 级。
 * </p>
 * <p>
 * **Validates: Requirements 1.4**
 * </p>
 */
@DisplayName("P1: BOQ 层级一致性属性测试")
class BoqHierarchyPropertyTest {

    private final BoqService boqService = new BoqService(null, null, null, null);

    /**
     * 生成随机合法编码列表（如 "1", "1.1", "1.1.1", "2", "2.1"）
     * 层级范围 1~4，每级段号范围 1~9
     */
    private List<String> generateRandomCodes() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> codes = new ArrayList<>();
        int topLevelCount = random.nextInt(1, 6); // 1~5 个顶层节点

        for (int i = 1; i <= topLevelCount; i++) {
            String topCode = String.valueOf(i);
            codes.add(topCode);

            // 每个顶层节点下随机生成子节点
            int secondLevelCount = random.nextInt(0, 4);
            for (int j = 1; j <= secondLevelCount; j++) {
                String secondCode = topCode + "." + j;
                codes.add(secondCode);

                int thirdLevelCount = random.nextInt(0, 3);
                for (int k = 1; k <= thirdLevelCount; k++) {
                    String thirdCode = secondCode + "." + k;
                    codes.add(thirdCode);

                    int fourthLevelCount = random.nextInt(0, 2);
                    for (int l = 1; l <= fourthLevelCount; l++) {
                        String fourthCode = thirdCode + "." + l;
                        codes.add(fourthCode);
                    }
                }
            }
        }
        return codes;
    }

    @RepeatedTest(50)
    @DisplayName("P1: 随机编码列表 → getParentCode 输出中 level > 1 的条目 parent 存在且 parent.level == current.level - 1")
    void testHierarchyConsistency() {
        List<String> codes = generateRandomCodes();

        for (String code : codes) {
            int currentLevel = code.split("\\.").length;
            String parentCode = boqService.getParentCode(code);

            if (currentLevel == 1) {
                // 顶层编码应该返回 null
                assertNull(parentCode,
                        "顶层编码 '" + code + "' 的 getParentCode 应返回 null");
            } else {
                // 非顶层编码的父编码不为 null
                assertNotNull(parentCode,
                        "非顶层编码 '" + code + "' 的 getParentCode 不应返回 null");

                // 父编码的层级 == 当前层级 - 1
                int parentLevel = parentCode.split("\\.").length;
                assertEquals(currentLevel - 1, parentLevel,
                        "编码 '" + code + "' 的父编码 '" + parentCode + "' 层级应为 " + (currentLevel - 1) + " 但实际为 " + parentLevel);

                // 父编码应该存在于编码列表中
                assertTrue(codes.contains(parentCode),
                        "编码 '" + code + "' 的父编码 '" + parentCode + "' 应存在于编码列表中");

                // 当前编码应该以父编码为前缀
                assertTrue(code.startsWith(parentCode + "."),
                        "编码 '" + code + "' 应以父编码 '" + parentCode + ".' 为前缀");
            }
        }
    }

    @RepeatedTest(50)
    @DisplayName("P1: 随机 N 级编码 → getParentCode 返回值的 '.' 分隔段数 == 输入段数 - 1")
    void testParentCodeLevelDecrementProperty() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 随机生成 2~4 级编码
        int levels = random.nextInt(2, 5);
        StringBuilder codeBuilder = new StringBuilder();
        for (int i = 0; i < levels; i++) {
            if (i > 0) codeBuilder.append(".");
            codeBuilder.append(random.nextInt(1, 20));
        }
        String code = codeBuilder.toString();

        String parentCode = boqService.getParentCode(code);

        assertNotNull(parentCode, "多层级编码 '" + code + "' 的 getParentCode 不应返回 null");

        int codeParts = code.split("\\.").length;
        int parentParts = parentCode.split("\\.").length;

        assertEquals(codeParts - 1, parentParts,
                "编码 '" + code + "'(段数=" + codeParts + ") 的父编码 '" + parentCode + "' 段数应为 " + (codeParts - 1));
    }
}
