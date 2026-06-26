package com.zwinsight.system;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.config.HealthMonitorConfig;
import com.zwinsight.system.domain.SysVersion;
import com.zwinsight.system.mapper.SysVersionMapper;
import com.zwinsight.system.service.SystemMetricsCollector;
import com.zwinsight.system.service.VersionManagerService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OpsPropertyTest — 数据运维后端属性测试（Property 23-25）。
 *
 * <p>覆盖 {@link VersionManagerService} 的版本号语义化校验、版本列表降序，以及
 * {@link HealthMonitorConfig} 的健康指标阈值告警逻辑。</p>
 *
 * <p><b>测试基础设施说明：</b></p>
 * <ul>
 *   <li>Property 23/24：使用 Mockito 模拟 {@link SysVersionMapper}（内存 store），无需真实数据库。
 *       通过 {@link TableInfoHelper#initTableInfo} 初始化 MyBatis-Plus 实体元数据，
 *       使 service 内部的 {@code LambdaQueryWrapper.orderByDesc/eq(lambda)} 在无 Spring 容器时也能解析列名。</li>
 *   <li>Property 24：mock {@code selectList} 模拟数据库 {@code ORDER BY release_date DESC} 行为
 *       （对内存 store 按 releaseDate 降序排序后返回），{@code selectOne}（LIMIT 1）返回发布日期最大的记录，
 *       以验证 service 契约（listAll 降序、getCurrent 取最新）。</li>
 *   <li>Property 25：实例化 {@link HealthMonitorConfig}，通过反射设置 {@code @Value} 私有阈值字段，
 *       使用 mock 的 {@link SystemMetricsCollector} 返回指定指标值；通过 Logback {@link ListAppender}
 *       挂载到 HealthMonitorConfig 的 logger 捕获日志，断言指标超阈值时产生 WARN 日志、未超阈值时不产生。</li>
 * </ul>
 */
class OpsPropertyTest {

    /** 语义化版本号正则（与 VersionManagerService 保持一致）。 */
    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 缓存），使无 Spring 容器时 LambdaQueryWrapper 可解析列名。
     */
    @BeforeContainer
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SysVersion.class);
    }

    // ==================== Property 23: 版本号语义化校验 ====================

    /**
     * Property 23: 不匹配 {@code ^\d+\.\d+\.\d+$} 的版本号应被 create() 拒绝（抛 BusinessException）。
     *
     * <p><b>Validates: Requirements 12.2</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 23: 版本号语义化校验 — 非法版本号被拒绝")
    void property23_invalidVersionRejected(@ForAll("invalidVersionStrings") String versionNo) {
        SysVersionMapper mapper = Mockito.mock(SysVersionMapper.class);
        // 唯一性校验默认放行，确保异常仅来自格式校验
        when(mapper.selectCount(any())).thenReturn(0L);
        VersionManagerService service = new VersionManagerService(mapper);

        Assertions.assertThatThrownBy(() -> service.create(versionNo, LocalDate.now(), "changelog"))
                .as("非法版本号 [%s] 应被拒绝", versionNo)
                .isInstanceOf(BusinessException.class);
    }

    /**
     * Property 23（正向）: 合法 x.y.z 版本号应创建成功并保留原始字段。
     *
     * <p><b>Validates: Requirements 12.2</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 23: 版本号语义化校验 — 合法版本号通过")
    void property23_validVersionAccepted(
            @ForAll @IntRange(min = 0, max = 999) int major,
            @ForAll @IntRange(min = 0, max = 999) int minor,
            @ForAll @IntRange(min = 0, max = 999) int patch) {

        String versionNo = major + "." + minor + "." + patch;
        // 自洽前提：生成的版本号确实符合语义化格式
        Assume.that(SEMVER.matcher(versionNo).matches());

        SysVersionMapper mapper = Mockito.mock(SysVersionMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(SysVersion.class))).thenReturn(1);
        VersionManagerService service = new VersionManagerService(mapper);

        LocalDate releaseDate = LocalDate.of(2026, 1, 1);
        SysVersion created = service.create(versionNo, releaseDate, "log-" + versionNo);

        Assertions.assertThat(created.getVersionNo()).isEqualTo(versionNo);
        Assertions.assertThat(created.getReleaseDate()).isEqualTo(releaseDate);
        Assertions.assertThat(created.getChangelog()).isEqualTo("log-" + versionNo);
        Mockito.verify(mapper).insert(any(SysVersion.class));
    }

    // ==================== Property 24: 版本列表按发布日期降序 ====================

    /**
     * Property 24: listAll() 返回结果按 releaseDate 降序排列，getCurrent() 返回发布日期最大的记录。
     *
     * <p><b>Validates: Requirements 12.3, 12.4</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 24: 版本列表按发布日期降序")
    void property24_listSortedDescAndCurrentIsLatest(
            @ForAll("distinctReleaseDates") List<LocalDate> dates) {

        // 内存 store：按生成顺序（未排序）构造版本记录
        List<SysVersion> store = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            SysVersion v = new SysVersion();
            v.setId((long) (i + 1));
            v.setVersionNo("1.0." + i);
            v.setReleaseDate(dates.get(i));
            store.add(v);
        }

        SysVersionMapper mapper = Mockito.mock(SysVersionMapper.class);
        // 模拟 DB ORDER BY release_date DESC
        when(mapper.selectList(any())).thenAnswer(inv -> store.stream()
                .sorted(Comparator.comparing(SysVersion::getReleaseDate).reversed())
                .collect(Collectors.toList()));
        // 模拟 ORDER BY release_date DESC LIMIT 1
        when(mapper.selectOne(any())).thenAnswer(inv -> store.stream()
                .max(Comparator.comparing(SysVersion::getReleaseDate))
                .orElse(null));

        VersionManagerService service = new VersionManagerService(mapper);

        List<SysVersion> result = service.listAll();
        List<LocalDate> resultDates = result.stream()
                .map(SysVersion::getReleaseDate).collect(Collectors.toList());

        // listAll 结果应严格按发布日期降序
        Assertions.assertThat(resultDates)
                .as("版本列表应按发布日期降序")
                .isSortedAccordingTo(Comparator.reverseOrder());

        // getCurrent 应返回发布日期最大的记录
        LocalDate maxDate = dates.stream().max(Comparator.naturalOrder()).orElseThrow();
        SysVersion current = service.getCurrent();
        Assertions.assertThat(current).isNotNull();
        Assertions.assertThat(current.getReleaseDate())
                .as("getCurrent 应返回最新发布日期的版本")
                .isEqualTo(maxDate);
    }

    // ==================== Property 25: 健康指标阈值告警 ====================

    /**
     * Property 25: 当 CPU/内存/磁盘指标超过对应阈值时产生 WARN 日志；未超过或指标不可用（负值）时不产生。
     *
     * <p>WARN 日志条数应等于"指标值非负且严格大于其阈值"的指标个数。</p>
     *
     * <p><b>Validates: Requirements 13.5</b></p>
     */
    @Property(tries = 150)
    @Label("Feature: p2-advanced, Property 25: 健康指标阈值告警")
    void property25_thresholdWarnLogged(
            @ForAll @IntRange(min = -1, max = 100) int cpu,
            @ForAll @IntRange(min = -1, max = 100) int memory,
            @ForAll @IntRange(min = -1, max = 100) int disk,
            @ForAll @IntRange(min = 0, max = 100) int cpuTh,
            @ForAll @IntRange(min = 0, max = 100) int memTh,
            @ForAll @IntRange(min = 0, max = 100) int diskTh) {

        SystemMetricsCollector collector = Mockito.mock(SystemMetricsCollector.class);
        when(collector.getCpuUsagePercent()).thenReturn((double) cpu);
        when(collector.getMemoryUsagePercent()).thenReturn((double) memory);
        when(collector.getDiskUsagePercent()).thenReturn((double) disk);

        HealthMonitorConfig config = new HealthMonitorConfig(collector);
        setThreshold(config, "cpuThreshold", cpuTh);
        setThreshold(config, "memoryThreshold", memTh);
        setThreshold(config, "diskThreshold", diskTh);

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            config.checkThresholds();
        } finally {
            detachAppender(appender);
        }

        long expectedWarns = 0;
        if (cpu >= 0 && cpu > cpuTh) expectedWarns++;
        if (memory >= 0 && memory > memTh) expectedWarns++;
        if (disk >= 0 && disk > diskTh) expectedWarns++;

        long actualWarns = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .count();

        Assertions.assertThat(actualWarns)
                .as("WARN 日志条数应等于超阈值的指标个数 (cpu=%d/th=%d, mem=%d/th=%d, disk=%d/th=%d)",
                        cpu, cpuTh, memory, memTh, disk, diskTh)
                .isEqualTo(expectedWarns);
    }

    /**
     * Property 25（示例）: 指标明显超过阈值时必然产生 WARN 日志。
     */
    @Test
    void property25_example_aboveThresholdWarns() {
        SystemMetricsCollector collector = Mockito.mock(SystemMetricsCollector.class);
        when(collector.getCpuUsagePercent()).thenReturn(95.0);
        when(collector.getMemoryUsagePercent()).thenReturn(50.0);
        when(collector.getDiskUsagePercent()).thenReturn(50.0);

        HealthMonitorConfig config = new HealthMonitorConfig(collector);
        setThreshold(config, "cpuThreshold", 90);
        setThreshold(config, "memoryThreshold", 85);
        setThreshold(config, "diskThreshold", 90);

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            config.checkThresholds();
        } finally {
            detachAppender(appender);
        }

        Assertions.assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("CPU"));
        Assertions.assertThat(appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN).count())
                .isEqualTo(1);
    }

    /**
     * Property 25（示例）: 所有指标均未超过阈值 / 不可用（负值）时不产生 WARN 日志。
     */
    @Test
    void property25_example_belowOrUnavailableNoWarn() {
        SystemMetricsCollector collector = Mockito.mock(SystemMetricsCollector.class);
        when(collector.getCpuUsagePercent()).thenReturn(10.0);   // 低于阈值
        when(collector.getMemoryUsagePercent()).thenReturn(-1.0); // 不可用
        when(collector.getDiskUsagePercent()).thenReturn(90.0);   // 等于阈值（非严格大于）

        HealthMonitorConfig config = new HealthMonitorConfig(collector);
        setThreshold(config, "cpuThreshold", 90);
        setThreshold(config, "memoryThreshold", 85);
        setThreshold(config, "diskThreshold", 90);

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            config.checkThresholds();
        } finally {
            detachAppender(appender);
        }

        Assertions.assertThat(appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN).count())
                .as("低于/等于阈值或指标不可用时不应产生 WARN 日志")
                .isZero();
    }

    // ==================== Arbitraries（数据提供器） ====================

    /**
     * 非法版本号字符串生成器：覆盖固定反例 + 随机不匹配语义化正则的字符串。
     */
    @Provide
    Arbitrary<String> invalidVersionStrings() {
        Arbitrary<String> fixed = Arbitraries.of(
                "", " ", "1", "1.2", "1.2.3.4", "v1.2.3", "1.2.3 ", " 1.2.3",
                "a.b.c", "1..2", "1.2.", ".1.2", "1.2.x", "01.02", "1,2,3",
                "1.2.3-beta", "latest", "1.2.3.0", "-1.2.3");
        Arbitrary<String> random = Arbitraries.strings()
                .withCharRange('!', 'z').ofMinLength(0).ofMaxLength(12)
                .filter(s -> !SEMVER.matcher(s).matches());
        return Arbitraries.oneOf(fixed, random)
                .filter(s -> !SEMVER.matcher(s).matches());
    }

    /**
     * 互不相同的发布日期列表（长度 1-15），保证存在唯一最大日期，便于断言 getCurrent。
     */
    @Provide
    Arbitrary<List<LocalDate>> distinctReleaseDates() {
        // 以 2000-01-01 为基准的偏移天数，去重保证日期互不相同
        return Arbitraries.integers().between(0, 20000)
                .map(days -> LocalDate.of(2000, 1, 1).plusDays(days))
                .list().uniqueElements().ofMinSize(1).ofMaxSize(15);
    }

    // ==================== 辅助方法 ====================

    /** 通过反射设置 HealthMonitorConfig 的 @Value 私有阈值字段。 */
    private static void setThreshold(HealthMonitorConfig config, String fieldName, double value) {
        try {
            Field field = HealthMonitorConfig.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setDouble(config, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("设置阈值字段失败: " + fieldName, e);
        }
    }

    /** 将 ListAppender 挂载到 HealthMonitorConfig 的 logger。 */
    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(HealthMonitorConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    /** 从 logger 卸载 ListAppender。 */
    private static void detachAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(HealthMonitorConfig.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
