package com.zwinsight.contract.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON 列泛型丢失回归（2026-10-05 线上 P0）。
 *
 * <p>MyBatis-Plus JacksonTypeHandler 按字段裸类型（List）反序列化，
 * {@code List<AffectedAccount>} 从 biz_change_event 读回后元素实为 LinkedHashMap，
 * validateCostDetails 遍历强转即 ClassCastException → 带成本明细的变更提交评估必 500。
 * 修复在实体 setter 单点归一化；本测试用 TypeHandler 等价的写入方式（裸 List 混入
 * LinkedHashMap/Integer 元素）锁死该行为，防再次回退。</p>
 */
@DisplayName("BizChangeEvent JSON 列泛型归一化")
class BizChangeEventJsonListTest {

    @Test
    @DisplayName("affectedAccounts 混入 LinkedHashMap（TypeHandler 读回形态）后仍按声明类型可遍历")
    void affectedAccountsNormalizesLinkedHashMapElements() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("accountId", 99651L);
        raw.put("deltaType", "INCREASE");
        raw.put("deltaAmount", new BigDecimal("35000.00"));

        List<Object> rawList = new ArrayList<>();
        rawList.add(raw);
        // 模拟 JacksonTypeHandler 丢失泛型后的写入路径：List 引用带声明类型，元素仍是 Map。
        // 只能强转 List 引用（擦除后合法）——逐元素强转在测试代码里就会先炸，复现不了线上路径
        @SuppressWarnings("unchecked")
        List<BizChangeEvent.AffectedAccount> polluted =
                (List<BizChangeEvent.AffectedAccount>) (List<?>) rawList;

        BizChangeEvent event = new BizChangeEvent();
        event.setAffectedAccounts(polluted);

        List<BizChangeEvent.AffectedAccount> resolved = event.resolveAffectedAccounts();
        assertThat(resolved).hasSize(1);
        BizChangeEvent.AffectedAccount account = resolved.get(0);
        assertThat(account.getAccountId()).isEqualTo(99651L);
        assertThat(account.getDeltaType()).isEqualTo("INCREASE");
        assertThat(account.getDeltaAmount()).isEqualByComparingTo("35000.00");
    }

    @Test
    @DisplayName("affectedWbsIds 混入 Integer（Jackson 小整数形态）后按 Long 遍历不炸")
    void affectedWbsIdsNormalizesIntegerElements() {
        List<Object> rawList = new ArrayList<>();
        rawList.add(Integer.valueOf(91001));
        @SuppressWarnings("unchecked")
        List<Long> polluted = (List<Long>) (List<?>) rawList;

        BizChangeEvent event = new BizChangeEvent();
        event.setAffectedWbsIds(polluted);

        long sum = 0;
        for (Long id : event.getAffectedWbsIds()) {
            sum += id;
        }
        assertThat(sum).isEqualTo(91001L);
    }

    @Test
    @DisplayName("supportingDocs 混入 LinkedHashMap 后字段可读")
    void supportingDocsNormalizesLinkedHashMapElements() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("url", "https://example.com/a.pdf");
        raw.put("name", "签证单.pdf");
        raw.put("type", "application/pdf");

        List<Object> rawList = new ArrayList<>();
        rawList.add(raw);
        @SuppressWarnings("unchecked")
        List<BizChangeEvent.SupportingDoc> polluted =
                (List<BizChangeEvent.SupportingDoc>) (List<?>) rawList;

        BizChangeEvent event = new BizChangeEvent();
        event.setSupportingDocs(polluted);

        assertThat(event.getSupportingDocs()).hasSize(1);
        assertThat(event.getSupportingDocs().get(0).getName()).isEqualTo("签证单.pdf");
    }

    @Test
    @DisplayName("null 与空列表原样透传（不产生额外对象）")
    void nullAndEmptyPassthrough() {
        BizChangeEvent event = new BizChangeEvent();
        event.setAffectedAccounts(null);
        event.setAffectedWbsIds(null);
        assertThat(event.resolveAffectedAccounts()).isEmpty();
        assertThat(event.getAffectedWbsIds()).isNull();

        event.setAffectedAccounts(new ArrayList<>());
        assertThat(event.resolveAffectedAccounts()).isEmpty();
    }
}
