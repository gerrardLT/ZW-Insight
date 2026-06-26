package com.zwinsight.file;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.service.PdfConvertService;
import com.zwinsight.file.service.ThymeleafRenderService;
import com.zwinsight.file.template.PrintTemplateMapper;
import com.zwinsight.file.template.PrintTemplateService;
import com.zwinsight.file.template.SysTemplate;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.assertj.core.api.Assertions;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * PrintTemplatePropertyTest — 打印模板 CRUD 逻辑属性测试（Feature: p2-advanced, Property 1-3）
 *
 * <p>使用 Mockito + 内存 store 模拟 {@link PrintTemplateMapper}，无需真实数据库即可验证
 * {@link PrintTemplateService} 的 create / getById / list 的核心契约。</p>
 *
 * <ul>
 *   <li>insert：为实体分配雪花式自增 id，并将其字段快照（深拷贝）写入内存 store，
 *       使 getById 返回独立对象，确保往返一致性断言有意义。</li>
 *   <li>selectById：按 id 返回 store 中的快照副本（非创建时的原始引用）。</li>
 *   <li>selectCount：按名称 + 业务类型计数（排除逻辑删除），支撑唯一性约束校验。</li>
 *   <li>selectPage：按业务类型过滤并排除逻辑删除记录，支撑业务类型过滤查询。</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 1.1, 1.3, 1.4, 1.5, 1.6</b></p>
 */
class PrintTemplatePropertyTest {

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 缓存），
     * 使 service 内部的 LambdaQueryWrapper.eq/orderBy(lambda) 在无 Spring 容器时也能解析列名。
     */
    @BeforeContainer
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SysTemplate.class);
    }

    // ==================== Property 1: 打印模板 CRUD 往返一致性 ====================

    /**
     * Property 1: 以随机 name / type / moduleCode / content / businessType 创建模板后，
     * 按 id 查询详情 SHALL 返回与创建时一致的字段值；当 templateType 为空白时应默认补全为 PRINT。
     *
     * <p><b>Feature: p2-advanced, Property 1: 打印模板 CRUD 往返一致性</b></p>
     * <p><b>Validates: Requirements 1.1, 1.3, 1.4</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 1: 打印模板 CRUD 往返一致性")
    void property1_crudRoundTripConsistent(
            @ForAll("templateNames") String name,
            @ForAll("optionalTypes") String type,
            @ForAll("moduleCodes") String moduleCode,
            @ForAll("templateContents") String content,
            @ForAll("businessTypes") String businessType) {

        Fixture f = newFixture();

        SysTemplate input = new SysTemplate();
        input.setTemplateName(name);
        input.setTemplateType(type);
        input.setModuleCode(moduleCode);
        input.setTemplateContent(content);
        input.setBusinessType(businessType);
        input.setEngineType("THYMELEAF");

        SysTemplate created = f.service.create(input);
        Assertions.assertThat(created.getId()).as("创建后应返回主键ID").isNotNull();

        // 当 templateType 为空白时，create 应默认补全为 PRINT
        String expectedType = (type == null || type.isBlank())
                ? PrintTemplateService.TEMPLATE_TYPE_PRINT : type;

        SysTemplate queried = f.service.getById(created.getId());
        Assertions.assertThat(queried).as("创建后应可按 id 查询到模板").isNotNull();
        Assertions.assertThat(queried.getTemplateName()).as("模板名称应往返一致").isEqualTo(name);
        Assertions.assertThat(queried.getTemplateType()).as("模板类型应往返一致(空白默认PRINT)").isEqualTo(expectedType);
        Assertions.assertThat(queried.getModuleCode()).as("模块编码应往返一致").isEqualTo(moduleCode);
        Assertions.assertThat(queried.getTemplateContent()).as("模板内容应往返一致").isEqualTo(content);
        Assertions.assertThat(queried.getBusinessType()).as("业务类型应往返一致").isEqualTo(businessType);
        Assertions.assertThat(queried.getEngineType()).as("引擎类型应往返一致").isEqualTo("THYMELEAF");
    }

    // ==================== Property 2: 模板业务类型过滤正确性 ====================

    /**
     * Property 2: 对包含多种业务类型（含部分逻辑删除）的模板集合，按某一业务类型分页查询，
     * 结果 SHALL 仅包含该业务类型且未被逻辑删除的模板，数量与预期一致。
     *
     * <p><b>Feature: p2-advanced, Property 2: 模板业务类型过滤正确性</b></p>
     * <p><b>Validates: Requirements 1.5, 1.6</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 2: 模板业务类型过滤正确性")
    void property2_businessTypeFilterCorrectness(
            @ForAll("seedSpecs") List<SeedSpec> specs,
            @ForAll("businessTypes") String filterType) {

        Fixture f = newFixture();

        // 直接向内存 store 种入记录（绕过 create 唯一性校验），名称保证全局唯一
        int seq = 0;
        for (SeedSpec spec : specs) {
            seed(f, "tpl_" + (seq++), spec.businessType, spec.deleted);
        }

        // 预期：该业务类型且未删除的记录数
        long expectedCount = specs.stream()
                .filter(s -> !s.deleted)
                .filter(s -> filterType.equals(s.businessType))
                .count();

        PageResult<SysTemplate> result = f.service.list(1, 1000, null, filterType, null);
        List<SysTemplate> records = result.getRecords();

        Assertions.assertThat(records)
                .as("过滤结果每条记录的业务类型都应等于过滤条件")
                .allMatch(t -> filterType.equals(t.getBusinessType()));
        Assertions.assertThat(records)
                .as("过滤结果不应包含逻辑删除的模板")
                .allMatch(t -> !isDeleted(t));
        Assertions.assertThat((long) records.size())
                .as("过滤结果数量应等于该业务类型下未删除模板的数量")
                .isEqualTo(expectedCount);
    }

    // ==================== Property 3: 模板名称唯一性约束 ====================

    /**
     * Property 3: 同一业务类型下，使用相同模板名称二次创建 SHALL 被拒绝（BusinessException, code=409）；
     * 而相同名称在不同业务类型下创建应被允许。
     *
     * <p><b>Feature: p2-advanced, Property 3: 模板名称唯一性约束</b></p>
     * <p><b>Validates: Requirements 1.6</b></p>
     */
    @Property(tries = 200)
    @Label("Feature: p2-advanced, Property 3: 模板名称唯一性约束")
    void property3_nameUniquenessConstraint(
            @ForAll("templateNames") String name,
            @ForAll("businessTypes") String businessType,
            @ForAll("businessTypes") String otherBusinessType) {

        Fixture f = newFixture();

        // 首次创建成功
        SysTemplate first = new SysTemplate();
        first.setTemplateName(name);
        first.setBusinessType(businessType);
        first.setTemplateContent("<p>x</p>");
        f.service.create(first);

        // 同名 + 同业务类型再次创建应被拒绝
        SysTemplate dup = new SysTemplate();
        dup.setTemplateName(name);
        dup.setBusinessType(businessType);
        dup.setTemplateContent("<p>y</p>");
        Assertions.assertThatThrownBy(() -> f.service.create(dup))
                .as("同业务类型下重复名称创建应被拒绝")
                .isInstanceOf(BusinessException.class);

        // 同名但不同业务类型应被允许
        Assume.that(!businessType.equals(otherBusinessType));
        SysTemplate diffBiz = new SysTemplate();
        diffBiz.setTemplateName(name);
        diffBiz.setBusinessType(otherBusinessType);
        diffBiz.setTemplateContent("<p>z</p>");
        Assertions.assertThatNoException()
                .as("相同名称在不同业务类型下创建应被允许")
                .isThrownBy(() -> f.service.create(diffBiz));
    }

    // ==================== Arbitraries (数据提供器) ====================

    /** 合法模板名称：长度 1-20 的小写字母串（非空白） */
    @Provide
    Arbitrary<String> templateNames() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(20);
    }

    /** 模板类型：可能为空白（触发默认 PRINT）或显式 EXPORT / PRINT / IMPORT */
    @Provide
    Arbitrary<String> optionalTypes() {
        return Arbitraries.of("", "  ", "PRINT", "EXPORT", "IMPORT");
    }

    /** 模块编码 */
    @Provide
    Arbitrary<String> moduleCodes() {
        return Arbitraries.of("material_inbound", "finance_invoice", "contract_main", "budget_detail");
    }

    /** 模板内容（HTML 片段，长度 1-50） */
    @Provide
    Arbitrary<String> templateContents() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(50)
                .map(s -> "<p>" + s + "</p>");
    }

    /** 业务类型枚举 */
    @Provide
    Arbitrary<String> businessTypes() {
        return Arbitraries.of("CONTRACT", "BUDGET", "MATERIAL", "FINANCE");
    }

    /** 种子记录集合：2-12 条，含随机业务类型与逻辑删除标记 */
    @Provide
    Arbitrary<List<SeedSpec>> seedSpecs() {
        Arbitrary<SeedSpec> one = Combinators.combine(
                businessTypes(),
                Arbitraries.of(true, false)
        ).as(SeedSpec::new);
        return one.list().ofMinSize(2).ofMaxSize(12);
    }

    // ==================== 测试夹具与内存基础设施 ====================

    /** 种子记录规格 */
    static class SeedSpec {
        final String businessType;
        final boolean deleted;

        SeedSpec(String businessType, boolean deleted) {
            this.businessType = businessType;
            this.deleted = deleted;
        }
    }

    private static class Fixture {
        PrintTemplateService service;
        List<SysTemplate> store;
    }

    private boolean isDeleted(SysTemplate t) {
        return t.getDeleted() != null && t.getDeleted() == 1;
    }

    /** 直接向内存 store 种入一条记录（绕过 service 唯一性校验，用于过滤测试前置数据） */
    private void seed(Fixture f, String name, String businessType, boolean deleted) {
        SysTemplate e = new SysTemplate();
        e.setId(f.store.size() + 100000L);
        e.setTemplateName(name);
        e.setTemplateType(PrintTemplateService.TEMPLATE_TYPE_PRINT);
        e.setBusinessType(businessType);
        e.setTemplateContent("<p>seed</p>");
        e.setIsDefault(0);
        e.setDeleted(deleted ? 1 : 0);
        f.store.add(e);
    }

    /** 浅克隆模板实体字段，用于在 insert 时生成独立快照副本 */
    private SysTemplate copyOf(SysTemplate src) {
        SysTemplate c = new SysTemplate();
        c.setId(src.getId());
        c.setTemplateName(src.getTemplateName());
        c.setTemplateType(src.getTemplateType());
        c.setModuleCode(src.getModuleCode());
        c.setFileId(src.getFileId());
        c.setTemplateContent(src.getTemplateContent());
        c.setIsDefault(src.getIsDefault());
        c.setEngineType(src.getEngineType());
        c.setBusinessType(src.getBusinessType());
        c.setDataQueryConfig(src.getDataQueryConfig());
        c.setDeleted(src.getDeleted());
        return c;
    }

    /**
     * 构建一套全新的（每次调用独立状态）service + 内存 mapper。
     */
    private Fixture newFixture() {
        final List<SysTemplate> store = new ArrayList<>();
        final AtomicLong idSeq = new AtomicLong(1);

        PrintTemplateMapper mapper = Mockito.mock(PrintTemplateMapper.class);
        ThymeleafRenderService thymeleaf = new ThymeleafRenderService();
        PdfConvertService pdf = Mockito.mock(PdfConvertService.class);

        // insert：分配 id 并写入 store 的独立副本
        when(mapper.insert(any(SysTemplate.class))).thenAnswer(inv -> {
            SysTemplate e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(idSeq.getAndIncrement());
            }
            store.add(copyOf(e));
            return 1;
        });

        // selectById：按 id 返回 store 中的快照副本
        when(mapper.selectById(any())).thenAnswer(inv -> {
            Long id = ((Number) inv.getArgument(0)).longValue();
            return store.stream()
                    .filter(t -> id.equals(t.getId()) && !isDeleted(t))
                    .findFirst().orElse(null);
        });

        // selectCount：唯一性校验，按 name(+businessType) 计数（排除逻辑删除）
        when(mapper.selectCount(any())).thenAnswer(inv -> {
            List<Object> vals = orderedParamValues(inv.getArgument(0));
            String name = (String) vals.get(0);
            String biz = vals.size() > 1 ? (String) vals.get(1) : null;
            return store.stream()
                    .filter(t -> !isDeleted(t))
                    .filter(t -> name.equals(t.getTemplateName()))
                    .filter(t -> biz == null ? t.getBusinessType() == null : biz.equals(t.getBusinessType()))
                    .count();
        });

        // selectPage：按业务类型过滤（排除逻辑删除）
        when(mapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<SysTemplate> pageParam = inv.getArgument(0);
            List<Object> vals = orderedParamValues(inv.getArgument(1));
            String bizFilter = vals.isEmpty() ? null : (String) vals.get(0);
            List<SysTemplate> matched = store.stream()
                    .filter(t -> !isDeleted(t))
                    .filter(t -> bizFilter == null || bizFilter.equals(t.getBusinessType()))
                    .collect(Collectors.toList());
            pageParam.setRecords(matched);
            pageParam.setTotal(matched.size());
            return pageParam;
        });

        Fixture f = new Fixture();
        f.store = store;
        f.service = new PrintTemplateService(mapper, thymeleaf, pdf);
        return f;
    }

    /**
     * 从 LambdaQueryWrapper 中按参数生成顺序（MPGENVALn）提取条件值。
     * 仅 eq/ne 等会写入参数值；orderBy 不产生参数。
     */
    private List<Object> orderedParamValues(Object wrapper) {
        if (!(wrapper instanceof LambdaQueryWrapper)) {
            return List.of();
        }
        LambdaQueryWrapper<?> w = (LambdaQueryWrapper<?>) wrapper;
        // paramNameValuePairs 惰性填充：先渲染 SQL 段
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
