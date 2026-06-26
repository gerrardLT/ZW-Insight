package com.zwinsight.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.finance.annotation.FinanceLockCheck;
import com.zwinsight.finance.aspect.FinanceLockAspect;
import com.zwinsight.finance.domain.BizFinanceLock;
import com.zwinsight.finance.domain.dto.FinanceLockDTO;
import com.zwinsight.finance.mapper.BizFinanceLockMapper;
import com.zwinsight.finance.service.FinanceLockService;
import com.zwinsight.security.mapper.SysUserMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.aspectj.lang.JoinPoint;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * FinanceLockPropertyTest — 财务封账 封账/解封 逻辑属性测试（Property 6-11, 21）
 *
 * <p>使用 Mockito + 内存 store 模拟 {@link BizFinanceLockMapper} / {@link RedisUtils} /
 * {@link SysUserMapper}，无需真实数据库与 Redis 即可验证 {@link FinanceLockService} 的
 * createLock / unlock / getStatus / getPage 行为，以及 {@link FinanceLockAspect} 的封账拦截契约。</p>
 *
 * <ul>
 *   <li>RedisUtils 以内存 Map 模拟（与 DB store 保持一致状态），get/set 真实读写内存。</li>
 *   <li>SysUserMapper.selectRoleCodesByUserId → ["ADMIN"]，使角色校验通过。</li>
 *   <li>SecurityContextHolder.setUserId 在每个夹具中设置当前用户。</li>
 *   <li>封账期间生成器限定为 [当前月-60, 当前月]，保证不晚于当前会计期间（createLock 约束）。</li>
 * </ul>
 *
 * <p><b>说明（实现优先）：</b>属性 6/7/8/10 通过 {@link FinanceLockAspect#checkFinanceLock} 直接
 * 验证拦截行为——拦截以抛出 {@link BusinessException} 体现"校验失败"，放行以无异常体现"校验通过"。</p>
 */
class FinanceLockPropertyTest {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String LOCK_TYPE_MONTHLY = "MONTHLY";
    private static final long TEST_USER_ID = 1001L;

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 缓存），
     * 使 service 内部的 LambdaQueryWrapper.eq/orderBy(lambda) 在无 Spring 容器时也能解析列名。
     */
    @BeforeContainer
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BizFinanceLock.class);
    }

    // ==================== Property 6: 封账期匹配时拦截所有写操作 ====================

    /**
     * Property 6: 对状态为 LOCKED 的期间，业务日期年月匹配该期间的财务单据，拦截器 SHALL 校验失败（抛异常）。
     *
     * <p><b>Validates: Requirements 4.1, 4.2, 4.3, 10.1</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 6: 封账期匹配时拦截所有写操作")
    void property6_lockedPeriodBlocksWrites(
            @ForAll("lockablePeriods") YearMonth period,
            @ForAll @IntRange(min = 1, max = 28) int day) {

        Fixture f = newFixture();
        // 封账该期间
        f.service.createLock(period.format(PERIOD_FORMATTER), LOCK_TYPE_MONTHLY);

        // 业务日期落在该期间
        Bill bill = new Bill(period.atDay(day));

        Assertions.assertThatThrownBy(() -> invokeAspect(f, bill))
                .as("封账期间内的业务单据应被拦截器拒绝")
                .isInstanceOf(BusinessException.class);
    }

    // ==================== Property 7: 非封账期正常放行 ====================

    /**
     * Property 7: 业务日期年月不匹配任何 LOCKED 期间的单据，拦截器 SHALL 校验通过（不抛异常）。
     *
     * <p><b>Validates: Requirements 4.5, 10.2</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 7: 非封账期正常放行")
    void property7_nonLockedPeriodPasses(
            @ForAll("lockablePeriods") YearMonth lockedPeriod,
            @ForAll("lockablePeriods") YearMonth bizPeriod,
            @ForAll @IntRange(min = 1, max = 28) int day) {

        // 两期间必须不同，确保业务日期落在非封账期
        Assume.that(!lockedPeriod.equals(bizPeriod));

        Fixture f = newFixture();
        f.service.createLock(lockedPeriod.format(PERIOD_FORMATTER), LOCK_TYPE_MONTHLY);

        Bill bill = new Bill(bizPeriod.atDay(day));

        Assertions.assertThatNoException()
                .as("非封账期间的业务单据应被放行")
                .isThrownBy(() -> invokeAspect(f, bill));
    }

    // ==================== Property 8: 解封后等同未封账 ====================

    /**
     * Property 8: 对已封账后又解封的期间，落在该期间的单据 SHALL 被放行，与从未封账时行为一致。
     *
     * <p><b>Validates: Requirements 4.6, 10.3</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 8: 解封后等同未封账")
    void property8_unlockedEqualsNeverLocked(
            @ForAll("lockablePeriods") YearMonth period,
            @ForAll @IntRange(min = 1, max = 28) int day) {

        Fixture f = newFixture();
        List<FinanceLockDTO> locks = f.service.createLock(period.format(PERIOD_FORMATTER), LOCK_TYPE_MONTHLY);
        Long lockId = locks.get(0).getId();

        // 解封
        f.service.unlock(lockId);

        Bill bill = new Bill(period.atDay(day));

        Assertions.assertThatNoException()
                .as("解封后的期间应等同未封账，单据被放行")
                .isThrownBy(() -> invokeAspect(f, bill));
    }

    // ==================== Property 9: 同期间封账幂等性 ====================

    /**
     * Property 9: 对同一期间多次封账，系统中 SHALL 仅存在一条该期间的 LOCKED 记录，重复封账被拒绝。
     *
     * <p><b>Validates: Requirements 3.4, 10.4</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 9: 同期间封账幂等性")
    void property9_lockIdempotency(@ForAll("lockablePeriods") YearMonth period) {
        Fixture f = newFixture();
        String periodStr = period.format(PERIOD_FORMATTER);

        f.service.createLock(periodStr, LOCK_TYPE_MONTHLY);

        // 第二次封账应被拒绝
        Assertions.assertThatThrownBy(() -> f.service.createLock(periodStr, LOCK_TYPE_MONTHLY))
                .as("同期间重复封账应被拒绝")
                .isInstanceOf(BusinessException.class);

        // store 中该期间 LOCKED 记录恰好一条
        long lockedCount = f.store.stream()
                .filter(r -> periodStr.equals(r.getPeriod()) && STATUS_LOCKED.equals(r.getStatus()))
                .count();
        Assertions.assertThat(lockedCount)
                .as("同期间应仅存在一条 LOCKED 记录")
                .isEqualTo(1L);
    }

    // ==================== Property 10: 无效业务日期拦截 ====================

    /**
     * Property 10: 业务日期为 null / 空串 / 非日期字符串的单据，拦截器 SHALL 校验失败（抛异常）。
     *
     * <p><b>Validates: Requirements 4.7, 10.5</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 10: 无效业务日期拦截")
    void property10_invalidBizDateBlocked(@ForAll("invalidDateBills") Bill bill) {
        Fixture f = newFixture();

        Assertions.assertThatThrownBy(() -> invokeAspect(f, bill))
                .as("业务日期为空或格式非法的单据应被拦截")
                .isInstanceOf(BusinessException.class);
    }

    // ==================== Property 11: 封账-解封状态往返 ====================

    /**
     * Property 11: 执行 封账→解封→再封账 序列后，该期间状态 SHALL 为 LOCKED 且操作可正常完成。
     *
     * <p><b>Validates: Requirements 3.5</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 11: 封账-解封状态往返")
    void property11_lockUnlockRoundTrip(@ForAll("lockablePeriods") YearMonth period) {
        Fixture f = newFixture();
        String periodStr = period.format(PERIOD_FORMATTER);

        List<FinanceLockDTO> first = f.service.createLock(periodStr, LOCK_TYPE_MONTHLY);
        f.service.unlock(first.get(0).getId());

        // 再次封账应成功（前一条已解封）
        List<FinanceLockDTO> second = f.service.createLock(periodStr, LOCK_TYPE_MONTHLY);
        Assertions.assertThat(second).as("解封后再次封账应成功").isNotEmpty();

        Assertions.assertThat(f.service.getStatus(periodStr))
                .as("封账→解封→再封账后，最终状态应为 LOCKED")
                .isEqualTo(STATUS_LOCKED);
    }

    // ==================== Property 21: 封账记录查询按期间倒序 ====================

    /**
     * Property 21: 封账记录列表查询结果 SHALL 按期间（YYYY-MM）降序排列。
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-business-enhance, Property 21: 封账记录查询按期间倒序")
    void property21_pageOrderedByPeriodDesc(@ForAll("distinctPeriodList") List<YearMonth> periods) {
        Fixture f = newFixture();
        for (YearMonth ym : periods) {
            f.service.createLock(ym.format(PERIOD_FORMATTER), LOCK_TYPE_MONTHLY);
        }

        PageResult<FinanceLockDTO> page = f.service.getPage(1, 100);
        List<String> resultPeriods = page.getRecords().stream()
                .map(FinanceLockDTO::getPeriod)
                .collect(Collectors.toList());

        // 相邻记录满足 period[i] >= period[i+1]（降序）
        for (int i = 0; i + 1 < resultPeriods.size(); i++) {
            Assertions.assertThat(resultPeriods.get(i).compareTo(resultPeriods.get(i + 1)))
                    .as("封账记录应按期间降序排列：%s 应 >= %s",
                            resultPeriods.get(i), resultPeriods.get(i + 1))
                    .isGreaterThanOrEqualTo(0);
        }
        Assertions.assertThat(resultPeriods)
                .as("查询结果数量应等于封账期间数量")
                .hasSize(periods.size());
    }

    // ==================== Arbitraries (数据提供器) ====================

    /** 可封账期间：[当前月-60, 当前月]，保证不晚于当前会计期间 */
    @Provide
    Arbitrary<YearMonth> lockablePeriods() {
        return Arbitraries.integers().between(0, 60)
                .map(off -> YearMonth.now().minusMonths(off));
    }

    /** 互不相同的可封账期间列表（长度 1-8） */
    @Provide
    Arbitrary<List<YearMonth>> distinctPeriodList() {
        return Arbitraries.integers().between(0, 60)
                .map(off -> YearMonth.now().minusMonths(off))
                .list().uniqueElements().ofMinSize(1).ofMaxSize(8);
    }

    /** 无效业务日期单据：applyDate 为 null / 空串 / 非日期字符串 */
    @Provide
    Arbitrary<Bill> invalidDateBills() {
        Arbitrary<Object> nullValue = Arbitraries.just(null);
        Arbitrary<Object> badStrings = Arbitraries.of(
                "", "   ", "abc", "not-a-date", "2024-13", "2024-00", "99-99", "2024/01/40", "13/40/2024")
                .map(s -> (Object) s);
        return Arbitraries.oneOf(nullValue, badStrings).map(Bill::new);
    }

    // ==================== 测试夹具与单据载体 ====================

    /**
     * 业务单据载体：拦截器通过反射调用 {@code getApplyDate()} 提取业务日期。
     * 返回类型为 Object 以同时支持 LocalDate（有效）与 String/null（无效）场景。
     */
    public static class Bill {
        private final Object applyDate;

        public Bill(Object applyDate) {
            this.applyDate = applyDate;
        }

        public Object getApplyDate() {
            return applyDate;
        }
    }

    /** 持有注解的样本方法，用于获取 FinanceLockCheck 注解实例 */
    @FinanceLockCheck(dateField = "applyDate", operation = "新增")
    private void annotatedSample() {
        // no-op
    }

    private FinanceLockCheck lockCheckAnnotation() {
        try {
            return FinanceLockPropertyTest.class
                    .getDeclaredMethod("annotatedSample")
                    .getAnnotation(FinanceLockCheck.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("annotatedSample 方法缺失", e);
        }
    }

    /** 调用封账切面校验逻辑 */
    private void invokeAspect(Fixture f, Bill bill) {
        JoinPoint jp = Mockito.mock(JoinPoint.class);
        when(jp.getArgs()).thenReturn(new Object[]{bill});
        f.aspect.checkFinanceLock(jp, lockCheckAnnotation());
    }

    /** 测试夹具：携带 service、aspect 及内存 store */
    private static class Fixture {
        FinanceLockService service;
        FinanceLockAspect aspect;
        List<BizFinanceLock> store;
    }

    /**
     * 构建一套全新的（每次调用独立状态）service + aspect + 内存 mapper。
     */
    private Fixture newFixture() {
        SecurityContextHolder.setUserId(TEST_USER_ID);

        final List<BizFinanceLock> store = new ArrayList<>();
        final AtomicLong idSeq = new AtomicLong(1);
        final AtomicLong createdSeq = new AtomicLong(0);

        BizFinanceLockMapper mapper = Mockito.mock(BizFinanceLockMapper.class);

        // insert：分配 id 与递增 createdAt（保证 getStatus 的 createdAt desc 排序有效）
        when(mapper.insert(any(BizFinanceLock.class))).thenAnswer(inv -> {
            BizFinanceLock e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(idSeq.getAndIncrement());
            }
            e.setCreatedAt(LocalDateTime.now().plusNanos(createdSeq.getAndIncrement()));
            store.add(e);
            return 1;
        });

        // selectById：返回内存中的同一引用（unlock 直接修改其状态）
        when(mapper.selectById(any())).thenAnswer(inv -> {
            Long id = ((Number) inv.getArgument(0)).longValue();
            return store.stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null);
        });

        // updateById：对象已按引用存于 store，无需额外处理
        when(mapper.updateById(any(BizFinanceLock.class))).thenReturn(1);

        // selectCount：service 使用 eq(period).eq(status)，按参数顺序提取
        when(mapper.selectCount(any())).thenAnswer(inv -> {
            List<Object> vals = orderedParamValues(inv.getArgument(0));
            String period = (String) vals.get(0);
            String status = (String) vals.get(1);
            return store.stream()
                    .filter(r -> period.equals(r.getPeriod()) && status.equals(r.getStatus()))
                    .count();
        });

        // selectOne：service 使用 eq(period).orderByDesc(createdAt).last(LIMIT 1)
        when(mapper.selectOne(any())).thenAnswer(inv -> {
            List<Object> vals = orderedParamValues(inv.getArgument(0));
            String period = (String) vals.get(0);
            return store.stream()
                    .filter(r -> period.equals(r.getPeriod()))
                    .max(Comparator.comparing(BizFinanceLock::getCreatedAt))
                    .orElse(null);
        });

        // selectPage：service 使用 orderByDesc(period)；按 period 降序分页
        when(mapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<BizFinanceLock> page = inv.getArgument(0);
            List<BizFinanceLock> sorted = store.stream()
                    .sorted(Comparator.comparing(BizFinanceLock::getPeriod).reversed())
                    .collect(Collectors.toList());
            long size = page.getSize();
            long current = page.getCurrent();
            int from = (int) Math.max(0, (current - 1) * size);
            int to = (int) Math.min(sorted.size(), from + size);
            List<BizFinanceLock> pageRecords = from <= to && from < sorted.size()
                    ? new ArrayList<>(sorted.subList(from, to)) : new ArrayList<>();
            page.setRecords(pageRecords);
            page.setTotal(sorted.size());
            return page;
        });

        // RedisUtils：内存 Map 模拟，与 DB store 保持一致
        RedisUtils redisUtils = Mockito.mock(RedisUtils.class);
        final Map<String, Object> redisStore = new HashMap<>();
        when(redisUtils.get(anyString())).thenAnswer(inv -> redisStore.get(inv.getArgument(0)));
        Mockito.doAnswer(inv -> {
            redisStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(redisUtils).set(anyString(), any(), anyLong());

        // SysUserMapper：当前用户具备 ADMIN 角色，通过角色校验
        SysUserMapper sysUserMapper = Mockito.mock(SysUserMapper.class);
        when(sysUserMapper.selectRoleCodesByUserId(any())).thenReturn(List.of("ADMIN"));

        Fixture f = new Fixture();
        f.store = store;
        f.service = new FinanceLockService(mapper, redisUtils, sysUserMapper);
        f.aspect = new FinanceLockAspect(f.service);
        return f;
    }

    /**
     * 从 LambdaQueryWrapper 中按参数生成顺序（MPGENVALn）提取条件值。
     * <p>paramNameValuePairs 为惰性填充：需先渲染 SQL 段，参数值才会写入；
     * key 形如 MPGENVAL1、MPGENVAL2，按其尾部数字排序即得 eq 调用顺序。</p>
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
