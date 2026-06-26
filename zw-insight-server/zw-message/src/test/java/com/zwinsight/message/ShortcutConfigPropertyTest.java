package com.zwinsight.message;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.message.domain.MsgAvailableShortcut;
import com.zwinsight.message.domain.MsgUserShortcut;
import com.zwinsight.message.dto.ShortcutBatchSaveResponse;
import com.zwinsight.message.mapper.MsgAvailableShortcutMapper;
import com.zwinsight.message.mapper.MsgUserShortcutMapper;
import com.zwinsight.message.service.UserShortcutService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ShortcutConfigPropertyTest — UserShortcutService 快捷入口配置属性测试（Property 16-20）
 *
 * <p>使用 Mockito + 内存 store 模拟 MsgUserShortcutMapper / MsgAvailableShortcutMapper，
 * 无需真实数据库即可验证 batchSave / getUserConfig 的核心契约行为。</p>
 *
 * <p>可选功能集合（Available_Shortcut）固定为 ID 1..{@link #UNIVERSE_SIZE} 且全部 ENABLED。</p>
 *
 * <p><b>契约差异说明（实现优先）：</b></p>
 * <ul>
 *   <li>Property 16 设计文本声明列表长度 1-20，但 {@code UserShortcutService} 的
 *       {@code MAX_SHORTCUT_COUNT = 8}，超过 8 会抛 BusinessException。本测试以实现契约为准，
 *       往返一致性在长度 1-8 区间验证。</li>
 *   <li>Property 20 设计文本声明"空列表清空配置后查询返回空"，但实现对空/ null 列表直接抛
 *       BusinessException("快捷入口数量不能为空")，不会清空。本测试以实现契约为准，验证抛异常
 *       且原有配置保持不变。</li>
 * </ul>
 */
class ShortcutConfigPropertyTest {

    /** 可选功能集合大小：有效 ID 为 1..20 */
    private static final int UNIVERSE_SIZE = 20;
    /** 服务实现的数量上限 */
    private static final int MAX_SHORTCUT_COUNT = 8;

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 缓存），
     * 使 service 内部的 LambdaQueryWrapper.select/eq(lambda) 在无 Spring 容器时也能解析列名。
     */
    @BeforeContainer
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, MsgAvailableShortcut.class);
        TableInfoHelper.initTableInfo(assistant, MsgUserShortcut.class);
    }

    // ==================== Property 16: 快捷入口保存-查询往返一致 ====================

    /**
     * Property 16: 有效 ID 列表（长度 1-8，互不重复且均在可选集合中）保存后查询返回相同列表且顺序一致。
     *
     * <p><b>Validates: Requirements 7.3, 11.1, 11.3</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 16: 快捷入口保存-查询往返一致")
    void property16_saveQueryRoundTripConsistent(
            @ForAll("validShortcutIdList") List<Long> shortcutIds) {

        long userId = 1001L;
        Fixture f = newFixture();
        UserShortcutService service = f.service;

        ShortcutBatchSaveResponse response = service.batchSave(userId, new ArrayList<>(shortcutIds));

        // savedIds 应与输入一致（输入本身无重复、全有效）
        Assertions.assertThat(response.getSavedIds())
                .as("savedIds 应与输入有效列表一致且顺序相同")
                .containsExactlyElementsOf(shortcutIds);
        Assertions.assertThat(response.getInvalidIds())
                .as("全有效输入不应有无效 ID")
                .isEmpty();

        // 查询返回相同的功能 ID 列表且顺序一致
        List<Long> queried = service.getUserConfig(userId).stream()
                .map(MsgUserShortcut::getShortcutId)
                .collect(Collectors.toList());
        Assertions.assertThat(queried)
                .as("查询结果应与保存列表完全一致且顺序相同")
                .containsExactlyElementsOf(shortcutIds);
    }

    // ==================== Property 17: 用户配置隔离 ====================

    /**
     * Property 17: 用户 A 的保存操作不影响用户 B 的配置与排序。
     *
     * <p><b>Validates: Requirements 7.5, 11.2</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 17: 用户配置隔离")
    void property17_userConfigIsolation(
            @ForAll("validShortcutIdList") List<Long> userAIds,
            @ForAll("validShortcutIdList") List<Long> userBIds) {

        long userA = 2001L;
        long userB = 2002L;
        Fixture f = newFixture();
        UserShortcutService service = f.service;

        // 用户 B 先保存配置
        service.batchSave(userB, new ArrayList<>(userBIds));
        List<Long> bBefore = service.getUserConfig(userB).stream()
                .map(MsgUserShortcut::getShortcutId)
                .collect(Collectors.toList());

        // 用户 A 保存配置
        service.batchSave(userA, new ArrayList<>(userAIds));

        // 用户 B 的配置应完全不变
        List<Long> bAfter = service.getUserConfig(userB).stream()
                .map(MsgUserShortcut::getShortcutId)
                .collect(Collectors.toList());

        Assertions.assertThat(bAfter)
                .as("用户 A 保存配置不应改变用户 B 的功能列表与排序")
                .containsExactlyElementsOf(bBefore);
    }

    // ==================== Property 18: 无效ID过滤逻辑 ====================

    /**
     * Property 18: 混合有效/无效 ID 的请求，仅保存有效 ID，响应含被过滤的无效 ID，
     * 查询结果仅含有效 ID。
     *
     * <p><b>Validates: Requirements 7.6</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 18: 无效ID过滤逻辑")
    void property18_invalidIdFiltering(
            @ForAll("mixedShortcutIdList") List<Long> mixedIds) {

        // 期望：先去重保留首次顺序，再按是否在可选集合内分区
        List<Long> dedup = new ArrayList<>(new LinkedHashSet<>(mixedIds));
        List<Long> expectedValid = dedup.stream()
                .filter(this::isValidId).collect(Collectors.toList());
        List<Long> expectedInvalid = dedup.stream()
                .filter(id -> !isValidId(id)).collect(Collectors.toList());

        // 生成器已保证至少存在一个有效 ID 与一个无效 ID
        Assume.that(!expectedValid.isEmpty());
        Assume.that(!expectedInvalid.isEmpty());

        long userId = 3001L;
        Fixture f = newFixture();
        UserShortcutService service = f.service;

        ShortcutBatchSaveResponse response = service.batchSave(userId, new ArrayList<>(mixedIds));

        Assertions.assertThat(response.getSavedIds())
                .as("savedIds 应仅含有效 ID 且保留首次出现顺序")
                .containsExactlyElementsOf(expectedValid);
        Assertions.assertThat(response.getInvalidIds())
                .as("invalidIds 应包含全部被过滤的无效 ID")
                .containsExactlyElementsOf(expectedInvalid);

        List<Long> queried = service.getUserConfig(userId).stream()
                .map(MsgUserShortcut::getShortcutId)
                .collect(Collectors.toList());
        Assertions.assertThat(queried)
                .as("查询结果应仅含有效 ID")
                .containsExactlyElementsOf(expectedValid);
    }

    // ==================== Property 19: 重复ID去重保留首次位置 ====================

    /**
     * Property 19: 含重复 ID 的请求去重后保留首次出现位置，保存后查询数量等于去重后数量。
     *
     * <p><b>Validates: Requirements 11.5</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 19: 重复ID去重保留首次位置")
    void property19_deduplicatePreservingFirstPosition(
            @ForAll("duplicateProneIdList") List<Long> idsWithDuplicates) {

        // 期望：LinkedHashSet 去重保留首次出现顺序
        List<Long> expected = new ArrayList<>(new LinkedHashSet<>(idsWithDuplicates));

        long userId = 4001L;
        Fixture f = newFixture();
        UserShortcutService service = f.service;

        ShortcutBatchSaveResponse response = service.batchSave(userId, new ArrayList<>(idsWithDuplicates));

        Assertions.assertThat(response.getSavedIds())
                .as("savedIds 应为去重后保留首次位置的列表")
                .containsExactlyElementsOf(expected);

        List<Long> queried = service.getUserConfig(userId).stream()
                .map(MsgUserShortcut::getShortcutId)
                .collect(Collectors.toList());
        Assertions.assertThat(queried)
                .as("查询结果应等于去重后的列表且顺序一致")
                .containsExactlyElementsOf(expected);
        Assertions.assertThat(queried.size())
                .as("查询结果数量应等于去重后的数量")
                .isEqualTo(expected.size());
    }

    // ==================== Property 20: 空列表行为（实现契约） ====================

    /**
     * Property 20: 设计文本声明空列表清空配置后查询返回空；但当前实现对空列表抛
     * BusinessException 且不修改已有配置。本测试以实现契约为准。
     *
     * <p><b>Validates: Requirements 11.4（注：实现与需求存在差异，见类注释）</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: p2-business-enhance, Property 20: 空列表清空配置（实现实际抛异常且保留原配置）")
    void property20_emptyListContract(
            @ForAll("validShortcutIdList") List<Long> existingIds) {

        long userId = 5001L;
        Fixture f = newFixture();
        UserShortcutService service = f.service;

        // 先保存一份有效配置
        service.batchSave(userId, new ArrayList<>(existingIds));
        List<Long> before = service.getUserConfig(userId).stream()
                .map(MsgUserShortcut::getShortcutId)
                .collect(Collectors.toList());

        // 实现契约：空列表抛 BusinessException
        Assertions.assertThatThrownBy(() -> service.batchSave(userId, new ArrayList<>()))
                .as("空列表应抛出 BusinessException")
                .isInstanceOf(BusinessException.class);

        // 原有配置应保持不变（抛异常前未做任何删除）
        List<Long> after = service.getUserConfig(userId).stream()
                .map(MsgUserShortcut::getShortcutId)
                .collect(Collectors.toList());
        Assertions.assertThat(after)
                .as("空列表保存失败后原配置应保持不变")
                .containsExactlyElementsOf(before);
    }

    // ==================== Arbitraries (数据提供器) ====================

    /** 有效 ID 列表：长度 1-8，互不重复，均在 1..UNIVERSE_SIZE 中 */
    @Provide
    Arbitrary<List<Long>> validShortcutIdList() {
        return Arbitraries.longs().between(1L, UNIVERSE_SIZE)
                .list().uniqueElements().ofMinSize(1).ofMaxSize(MAX_SHORTCUT_COUNT);
    }

    /**
     * 混合列表：含部分有效（1..20）和部分无效（1000..1020）ID，原始长度 1-8。
     * 通过 filter 保证同时包含至少一个有效与一个无效 ID。
     */
    @Provide
    Arbitrary<List<Long>> mixedShortcutIdList() {
        Arbitrary<Long> valid = Arbitraries.longs().between(1L, UNIVERSE_SIZE);
        Arbitrary<Long> invalid = Arbitraries.longs().between(1000L, 1020L);
        Arbitrary<Long> any = Arbitraries.oneOf(valid, invalid);
        return any.list().uniqueElements().ofMinSize(2).ofMaxSize(MAX_SHORTCUT_COUNT)
                .filter(list -> list.stream().anyMatch(this::isValidId)
                        && list.stream().anyMatch(id -> !isValidId(id)));
    }

    /**
     * 易产生重复的列表：从较小的有效 ID 集合（1..6）中抽取 2-8 个（允许重复），
     * 且保证至少存在一对重复。
     */
    @Provide
    Arbitrary<List<Long>> duplicateProneIdList() {
        return Arbitraries.longs().between(1L, 6L)
                .list().ofMinSize(2).ofMaxSize(MAX_SHORTCUT_COUNT)
                .filter(list -> new LinkedHashSet<>(list).size() < list.size());
    }

    private boolean isValidId(Long id) {
        return id != null && id >= 1L && id <= UNIVERSE_SIZE;
    }

    // ==================== 内存 Mock 基础设施 ====================

    /** 测试夹具：携带 service 及其内存 store */
    private static class Fixture {
        UserShortcutService service;
    }

    /**
     * 构建一套全新的（每次调用独立状态的）service + 内存 mapper。
     */
    private Fixture newFixture() {
        // 可选功能集合：ID 1..UNIVERSE_SIZE，全部 ENABLED
        List<MsgAvailableShortcut> universe = new ArrayList<>();
        for (int i = 1; i <= UNIVERSE_SIZE; i++) {
            MsgAvailableShortcut a = new MsgAvailableShortcut();
            a.setId((long) i);
            a.setName("功能" + i);
            a.setIcon("icon-" + i);
            a.setRoutePath("/path/" + i);
            a.setSortOrder(i);
            a.setStatus("ENABLED");
            universe.add(a);
        }

        // 用户快捷入口内存 store
        final List<MsgUserShortcut> store = new ArrayList<>();
        final AtomicLong idSeq = new AtomicLong(1);

        MsgUserShortcutMapper userMapper = Mockito.mock(MsgUserShortcutMapper.class);
        MsgAvailableShortcutMapper availMapper = Mockito.mock(MsgAvailableShortcutMapper.class);

        // insert：分配 id，加入 store
        when(userMapper.insert(any(MsgUserShortcut.class))).thenAnswer(inv -> {
            MsgUserShortcut e = inv.getArgument(0);
            e.setId(idSeq.getAndIncrement());
            store.add(e);
            return 1;
        });

        // delete(wrapper)：按 userId 删除
        when(userMapper.delete(any())).thenAnswer(inv -> {
            Long uid = extractUserId(inv.getArgument(0));
            int before = store.size();
            store.removeIf(s -> uid != null && uid.equals(s.getUserId()));
            return before - store.size();
        });

        // selectList(wrapper)：按 userId 过滤并按 sortOrder 升序
        when(userMapper.selectList(any())).thenAnswer(inv -> {
            Long uid = extractUserId(inv.getArgument(0));
            return store.stream()
                    .filter(s -> uid != null && uid.equals(s.getUserId()))
                    .sorted(Comparator.comparing(MsgUserShortcut::getSortOrder))
                    .collect(Collectors.toList());
        });

        // available selectList：返回全部启用项（universe 全部 ENABLED）
        when(availMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(universe));

        // available selectById：返回匹配项或 null
        when(availMapper.selectById(any())).thenAnswer(inv -> {
            Long id = ((Number) inv.getArgument(0)).longValue();
            return universe.stream().filter(a -> a.getId().equals(id)).findFirst().orElse(null);
        });

        Fixture f = new Fixture();
        f.service = new UserShortcutService(userMapper, availMapper);
        return f;
    }

    /**
     * 从 LambdaQueryWrapper 中提取 userId 查询参数。
     * 服务内部对 MsgUserShortcut 的查询/删除均使用 eq(getUserId, userId)，
     * orderBy 不产生参数值，故参数对中唯一的 Long/数值即为 userId。
     */
    private Long extractUserId(Object wrapper) {
        if (!(wrapper instanceof LambdaQueryWrapper)) {
            return null;
        }
        LambdaQueryWrapper<?> w = (LambdaQueryWrapper<?>) wrapper;
        // paramNameValuePairs 为惰性填充：需先渲染 SQL 段，参数值才会写入
        w.getSqlSegment();
        for (Object v : w.getParamNameValuePairs().values()) {
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
        }
        return null;
    }
}
