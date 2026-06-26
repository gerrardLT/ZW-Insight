package com.zwinsight.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizTaxRate;
import com.zwinsight.finance.domain.dto.TaxRateDTO;
import com.zwinsight.finance.mapper.BizTaxRateMapper;
import com.zwinsight.finance.service.TaxRateService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TaxRatePropertyTest — 税率字典管理 逻辑属性测试（Property 12-15, 22）
 *
 * <p>使用 Mockito + 内存 store 模拟 {@link BizTaxRateMapper}，无需真实数据库即可验证
 * {@link TaxRateService} 的 create / update / delete / listEnabled / listAll 行为。</p>
 *
 * <ul>
 *   <li>insert：分配自增 id 与递增 createdAt（保证列表按 createTime 升序排序可验证）。</li>
 *   <li>selectById：返回内存中的同一引用（update/delete 直接修改其字段）。</li>
 *   <li>updateById：对象按引用存于 store，无需额外处理。</li>
 *   <li>selectList：依据 wrapper 参数判断 listEnabled（eq status）或 listAll（无条件），按 createdAt 升序。</li>
 *   <li>selectCount：按 name 参数计数（含 DISABLED），并在 update 场景排除 excludeId，
 *       支撑名称唯一性约束。</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 5.1–5.7</b></p>
 */
class TaxRatePropertyTest {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 缓存），
     * 使 service 内部的 LambdaQueryWrapper.eq/orderBy(lambda) 在无 Spring 容器时也能解析列名。
     */
    @BeforeContainer
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizTaxRate.class);
    }

    // ==================== Property 12: 税率保存-查询往返一致 ====================

    /**
     * Property 12: 对合法名称(1-30)与数值(0.01-99.99,≤2位小数)，新增后查询 SHALL 返回相同名称与数值；
     * 修改后查询 SHALL 返回更新后的数值。
     *
     * <p><b>Validates: Requirements 5.1, 5.2</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 12: 税率保存-查询往返一致")
    void property12_saveQueryRoundTrip(
            @ForAll("validNames") String name,
            @ForAll("validValues") BigDecimal value,
            @ForAll("validValues") BigDecimal newValue) {

        Fixture f = newFixture();

        TaxRateDTO created = f.service.create(name, value);
        Assertions.assertThat(created.getId()).as("新增后应返回主键ID").isNotNull();

        // 查询往返：通过 listAll 按 id 定位，验证名称与数值一致
        TaxRateDTO queried = findById(f.service.listAll(), created.getId());
        Assertions.assertThat(queried).as("新增记录应可被查询到").isNotNull();
        Assertions.assertThat(queried.getName()).as("名称应往返一致").isEqualTo(name);
        Assertions.assertThat(queried.getRateValue())
                .as("数值应往返一致").isEqualByComparingTo(value);

        // 修改数值后再次查询，验证更新生效
        f.service.update(created.getId(), name, newValue);
        TaxRateDTO afterUpdate = findById(f.service.listAll(), created.getId());
        Assertions.assertThat(afterUpdate.getRateValue())
                .as("修改后查询应返回更新后的数值").isEqualByComparingTo(newValue);
    }

    // ==================== Property 13: 税率逻辑删除保留数据 ====================

    /**
     * Property 13: 对已启用记录执行删除，状态 SHALL 变为 DISABLED，且全量查询 listAll 仍可见。
     *
     * <p><b>Validates: Requirements 5.3</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 13: 税率逻辑删除保留数据")
    void property13_logicalDeleteRetainsData(
            @ForAll("validNames") String name,
            @ForAll("validValues") BigDecimal value) {

        Fixture f = newFixture();
        TaxRateDTO created = f.service.create(name, value);

        f.service.delete(created.getId());

        TaxRateDTO afterDelete = findById(f.service.listAll(), created.getId());
        Assertions.assertThat(afterDelete)
                .as("逻辑删除后全量查询仍应可见该记录").isNotNull();
        Assertions.assertThat(afterDelete.getStatus())
                .as("删除应将状态置为 DISABLED").isEqualTo(STATUS_DISABLED);

        // listEnabled 不应再包含该记录
        Assertions.assertThat(findById(f.service.listEnabled(), created.getId()))
                .as("逻辑删除后启用列表不应再包含该记录").isNull();
    }

    // ==================== Property 14: 税率名称唯一性约束 ====================

    /**
     * Property 14: 对已存在的名称（无论 ENABLED 还是 DISABLED），新增/修改为相同名称 SHALL 被拒绝。
     *
     * <p><b>Validates: Requirements 5.4</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 14: 税率名称唯一性约束")
    void property14_nameUniqueness(
            @ForAll("validNames") String existingName,
            @ForAll("validValues") BigDecimal value,
            @ForAll boolean existingDisabled) {

        Fixture f = newFixture();

        // 种入一条已存在记录（ENABLED 或 DISABLED）
        seed(f, existingName, value, existingDisabled ? STATUS_DISABLED : STATUS_ENABLED);

        // 新增同名应被拒绝
        Assertions.assertThatThrownBy(() -> f.service.create(existingName, value))
                .as("新增重复名称应被拒绝").isInstanceOf(BusinessException.class);

        // 另建一条不同名称的记录，再修改为已存在名称应被拒绝
        TaxRateDTO other = f.service.create(existingName + "_other", value);
        Assertions.assertThatThrownBy(() -> f.service.update(other.getId(), existingName, value))
                .as("修改为重复名称应被拒绝").isInstanceOf(BusinessException.class);

        // 将记录修改为其自身名称（不变）应被允许（excludeId 排除自身）
        Assertions.assertThatNoException()
                .as("修改为自身名称不应触发唯一性冲突")
                .isThrownBy(() -> f.service.update(other.getId(), existingName + "_other", value));
    }

    // ==================== Property 15: 税率数值与名称校验 ====================

    /**
     * Property 15: 数值越界([0.01,99.99]之外)或>2位小数 SHALL 被拒绝；名称为空或>30字符 SHALL 被拒绝。
     *
     * <p><b>Validates: Requirements 5.5, 5.6, 5.7</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 15: 税率数值与名称校验 - 非法数值")
    void property15_invalidValueRejected(
            @ForAll("validNames") String name,
            @ForAll("invalidValues") BigDecimal invalidValue) {

        Fixture f = newFixture();
        Assertions.assertThatThrownBy(() -> f.service.create(name, invalidValue))
                .as("非法数值[%s]应被拒绝", invalidValue)
                .isInstanceOf(BusinessException.class);
    }

    /**
     * Property 15（续）: 名称为空、空白或超过30字符 SHALL 被拒绝。
     *
     * <p><b>Validates: Requirements 5.5, 5.6, 5.7</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 15: 税率数值与名称校验 - 非法名称")
    void property15_invalidNameRejected(
            @ForAll("invalidNames") String invalidName,
            @ForAll("validValues") BigDecimal value) {

        Fixture f = newFixture();
        Assertions.assertThatThrownBy(() -> f.service.create(invalidName, value))
                .as("非法名称应被拒绝").isInstanceOf(BusinessException.class);
    }

    // ==================== Property 22: 税率列表按创建时间升序 ====================

    /**
     * Property 22: listEnabled 查询结果 SHALL 按 createTime 升序排列。
     *
     * <p><b>Validates: Requirements 5.1</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 22: 税率列表按创建时间升序")
    void property22_listOrderedByCreateTimeAsc(@ForAll @IntRange(min = 2, max = 12) int count) {
        Fixture f = newFixture();

        // 插入 count 条记录（名称互不相同）
        for (int i = 0; i < count; i++) {
            f.service.create("rate_" + i, new BigDecimal("13.00"));
        }

        List<TaxRateDTO> list = f.service.listEnabled();
        Assertions.assertThat(list).as("启用列表应包含全部插入记录").hasSize(count);

        // 相邻记录满足 createTime[i] <= createTime[i+1]（升序）
        for (int i = 0; i + 1 < list.size(); i++) {
            Assertions.assertThat(list.get(i).getCreateTime())
                    .as("税率列表应按创建时间升序排列")
                    .isBeforeOrEqualTo(list.get(i + 1).getCreateTime());
        }
    }

    // ==================== Arbitraries (数据提供器) ====================

    /** 合法名称：长度 1-30 的字母字符串（非空白） */
    @Provide
    Arbitrary<String> validNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(20);
    }

    /** 合法数值：0.01-99.99，2 位小数（由 1..9999 分整除以 100 得到） */
    @Provide
    Arbitrary<BigDecimal> validValues() {
        return Arbitraries.integers().between(1, 9999)
                .map(cents -> new BigDecimal(cents).movePointLeft(2));
    }

    /** 非法数值：&lt;0.01、&gt;99.99 或 &gt;2 位小数 */
    @Provide
    Arbitrary<BigDecimal> invalidValues() {
        Arbitrary<BigDecimal> tooSmall = Arbitraries.of(
                new BigDecimal("0.00"), new BigDecimal("0.009"),
                new BigDecimal("-0.01"), new BigDecimal("-5.00"));
        Arbitrary<BigDecimal> tooLarge = Arbitraries.integers().between(100, 100000)
                .map(BigDecimal::new);
        // >2 位小数但落在区间内：cents(1..9999)/100 再追加非零第三位小数
        Arbitrary<BigDecimal> tooManyDecimals = Arbitraries.integers().between(1, 9999)
                .map(cents -> new BigDecimal(cents).movePointLeft(2).add(new BigDecimal("0.001")));
        return Arbitraries.oneOf(tooSmall, tooLarge, tooManyDecimals);
    }

    /** 非法名称：null / 空串 / 纯空白 / 超过30字符 */
    @Provide
    Arbitrary<String> invalidNames() {
        Arbitrary<String> nullName = Arbitraries.just(null);
        Arbitrary<String> blanks = Arbitraries.of("", "   ", "\t", "\n  ");
        Arbitrary<String> tooLong = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(31).ofMaxLength(60);
        return Arbitraries.oneOf(nullName, blanks, tooLong);
    }

    // ==================== 测试夹具 ====================

    private static class Fixture {
        TaxRateService service;
        List<BizTaxRate> store;
    }

    /** 直接向内存 store 种入一条记录（绕过 service 校验，用于唯一性前置数据） */
    private void seed(Fixture f, String name, BigDecimal value, String status) {
        BizTaxRate e = new BizTaxRate();
        e.setId(f.store.size() + 100000L);
        e.setName(name);
        e.setRateValue(value);
        e.setStatus(status);
        e.setCreatedAt(LocalDateTime.now());
        f.store.add(e);
    }

    private TaxRateDTO findById(List<TaxRateDTO> list, Long id) {
        return list.stream().filter(d -> id.equals(d.getId())).findFirst().orElse(null);
    }

    /**
     * 构建一套全新的（每次调用独立状态）service + 内存 mapper。
     */
    private Fixture newFixture() {
        final List<BizTaxRate> store = new ArrayList<>();
        final AtomicLong idSeq = new AtomicLong(1);
        final AtomicLong createdSeq = new AtomicLong(0);

        BizTaxRateMapper mapper = Mockito.mock(BizTaxRateMapper.class);

        // insert：分配 id 与递增 createdAt
        when(mapper.insert(any(BizTaxRate.class))).thenAnswer(inv -> {
            BizTaxRate e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(idSeq.getAndIncrement());
            }
            e.setCreatedAt(LocalDateTime.now().plusNanos(createdSeq.getAndIncrement()));
            store.add(e);
            return 1;
        });

        // selectById：返回内存中的同一引用
        when(mapper.selectById(any())).thenAnswer(inv -> {
            Long id = ((Number) inv.getArgument(0)).longValue();
            return store.stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null);
        });

        // updateById：对象已按引用存于 store
        when(mapper.updateById(any(BizTaxRate.class))).thenReturn(1);

        // selectCount：唯一性校验，service 使用 eq(name)[.ne(id)]，按 name 计数（含 DISABLED）
        when(mapper.selectCount(any())).thenAnswer(inv -> {
            List<Object> vals = orderedParamValues(inv.getArgument(0));
            String name = (String) vals.get(0);
            Long excludeId = vals.size() > 1 ? ((Number) vals.get(1)).longValue() : null;
            return store.stream()
                    .filter(r -> name.equals(r.getName()))
                    .filter(r -> excludeId == null || !excludeId.equals(r.getId()))
                    .count();
        });

        // selectList：listEnabled（eq status）或 listAll（无条件），均按 createdAt 升序
        when(mapper.selectList(any())).thenAnswer(inv -> {
            List<Object> vals = orderedParamValues(inv.getArgument(0));
            return store.stream()
                    .filter(r -> vals.isEmpty() || vals.get(0).equals(r.getStatus()))
                    .sorted(Comparator.comparing(BizTaxRate::getCreatedAt))
                    .collect(Collectors.toList());
        });

        Fixture f = new Fixture();
        f.store = store;
        f.service = new TaxRateService(mapper);
        return f;
    }

    /**
     * 从 LambdaQueryWrapper 中按参数生成顺序（MPGENVALn）提取条件值。
     */
    private List<Object> orderedParamValues(Object wrapper) {
        if (!(wrapper instanceof LambdaQueryWrapper)) {
            return List.of();
        }
        LambdaQueryWrapper<?> w = (LambdaQueryWrapper<?>) wrapper;
        w.getSqlSegment();
        return w.getParamNameValuePairs().entrySet().stream()
                .sorted(Comparator.comparingInt(e -> trailingInt(e.getKey())))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private int trailingInt(String key) {
        String digits = key.replaceAll("\\D+", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}
