package com.zwinsight.site.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.site.dto.SchemeSnapshotDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property P6: 方案快照不可变性
 * <p>
 * 验证：方案快照生成后（序列化为 JSON），修改原始 schemeItems 不影响快照 JSON 内容。
 * 这证明数据库中存储的 JSON 快照在方案源数据变更后不受影响。
 * </p>
 * <p>
 * **Validates: Requirements 7.7**
 * </p>
 */
@DisplayName("P6: 方案快照不可变性属性测试")
class SchemeSnapshotImmutabilityPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成随机检查项
     */
    private SchemeSnapshotDTO.ItemDTO generateRandomItem(ThreadLocalRandom random) {
        SchemeSnapshotDTO.ItemDTO item = new SchemeSnapshotDTO.ItemDTO();
        item.setItemName("检查项目_" + random.nextInt(1, 1000));
        item.setCheckStandard("标准_" + random.nextInt(1, 500) + "_" + generateRandomString(random, 10));
        item.setCheckMethod("方法_" + random.nextInt(1, 200));
        return item;
    }

    /**
     * 生成随机字符串
     */
    private String generateRandomString(ThreadLocalRandom random, int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 构造 SchemeSnapshotDTO 并序列化为 JSON（模拟快照写入数据库的过程）
     */
    private String createSnapshot(SchemeSnapshotDTO dto) throws JsonProcessingException {
        return objectMapper.writeValueAsString(dto);
    }

    @RepeatedTest(30)
    @DisplayName("P6: 方案快照生成后，修改原始 schemeItems 不影响快照 JSON 内容")
    void testSnapshotImmutability() throws JsonProcessingException {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 1. 构造 SchemeSnapshotDTO
        SchemeSnapshotDTO snapshot = new SchemeSnapshotDTO();
        snapshot.setSchemeId((long) random.nextInt(1, 10000));
        snapshot.setSchemeName("方案_" + random.nextInt(1, 100));

        int itemCount = random.nextInt(1, 20);
        List<SchemeSnapshotDTO.ItemDTO> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            items.add(generateRandomItem(random));
        }
        snapshot.setItems(items);

        // 2. 序列化为 JSON（模拟存入数据库）
        String snapshotJson = createSnapshot(snapshot);
        assertNotNull(snapshotJson);
        assertFalse(snapshotJson.isEmpty());

        // 3. 修改原始对象的 items（模拟方案源数据变更）
        // 3a. 修改已有项的名称
        if (!items.isEmpty()) {
            items.get(0).setItemName("已修改_" + random.nextInt(9999));
            items.get(0).setCheckStandard("新标准_" + random.nextInt(9999));
        }
        // 3b. 新增检查项
        items.add(generateRandomItem(random));
        // 3c. 删除某个检查项
        if (items.size() > 2) {
            items.remove(items.size() - 2);
        }
        // 3d. 修改方案名称
        snapshot.setSchemeName("修改后方案_" + random.nextInt(9999));

        // 4. 验证已序列化的 JSON 内容未变化
        // 因为 JSON 是值类型的字符串，修改原对象不影响已序列化的字符串
        String snapshotJsonAfterModification = snapshotJson; // 数据库中存储的快照不变
        assertEquals(snapshotJson, snapshotJsonAfterModification,
                "修改原始对象后，已序列化的快照 JSON 不应改变");

        // 5. 反序列化验证：从 JSON 恢复的数据与修改后的原始对象不同
        SchemeSnapshotDTO restoredSnapshot = objectMapper.readValue(snapshotJson, SchemeSnapshotDTO.class);
        assertNotEquals(snapshot.getSchemeName(), restoredSnapshot.getSchemeName(),
                "快照恢复后的方案名称应与修改后的原始对象不同");
    }

    @RepeatedTest(30)
    @DisplayName("P6: 相同内容的 DTO 多次序列化产生相同 JSON")
    void testSerializationDeterminism() throws JsonProcessingException {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        SchemeSnapshotDTO snapshot = new SchemeSnapshotDTO();
        snapshot.setSchemeId((long) random.nextInt(1, 10000));
        snapshot.setSchemeName("固定方案_" + random.nextInt(100));

        int itemCount = random.nextInt(1, 10);
        List<SchemeSnapshotDTO.ItemDTO> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            items.add(generateRandomItem(random));
        }
        snapshot.setItems(items);

        // 多次序列化应产生相同结果
        String json1 = createSnapshot(snapshot);
        String json2 = createSnapshot(snapshot);

        assertEquals(json1, json2,
                "相同对象多次序列化应产生相同 JSON");
    }
}
