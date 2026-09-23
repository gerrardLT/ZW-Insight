package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.mapper.BizRiskRegisterMapper;
import com.zwinsight.dashboard.risk.ProjectOwnerResolver;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RiskScanService 单元测试（V2026_59 风险中心台账语义）
 * <p>核心断言：riskCode 幂等 upsert、IGNORED 人工消音不被自动重开、
 * 判级升高强制重开、风险消失自动 RESOLVED、单规则异常隔离不中断整体扫描。</p>
 * <p><b>不在本层验证的语义</b>：“自动关闭仅扫 OPEN/PROCESSING、IGNORED 不被自动 RESOLVED”
 * 由 selectList 的 SQL IN 条件决定，纯 Mockito 环境无法解析 lambda 列名
 * （报 can not find lambda cache），改由 L3 真实接口验证：
 * keys/test-api-risk.sh 的“IGNORED 风险经扫描后仍为 IGNORED”用例。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskScanServiceTest {

    @Mock private BizRiskRegisterMapper riskMapper;
    @Mock private BizProjectMapper projectMapper;
    @Mock private ProjectOwnerResolver ownerResolver;

    /** 可控测试桩规则：按构造入参返回固定 findings 或抛异常 */
    private static class StubRule implements RiskRule {
        private final String type;
        private final List<RiskFinding> findings;
        private final boolean shouldThrow;

        StubRule(String type, List<RiskFinding> findings, boolean shouldThrow) {
            this.type = type;
            this.findings = findings;
            this.shouldThrow = shouldThrow;
        }

        @Override
        public String riskType() {
            return type;
        }

        @Override
        public List<RiskFinding> evaluate() {
            if (shouldThrow) {
                throw new IllegalStateException("规则执行异常（测试注入）");
            }
            return findings;
        }
    }

    private RiskFinding finding(String type, Long projectId, String severity, String bizRefType, Long bizRefId) {
        return new RiskFinding(type, projectId, severity, "测试风险标题",
                new BigDecimal("100000"), "{\"k\":1}", "测试处置建议", bizRefType, bizRefId, "{\"threshold\":80}");
    }

    private BizRiskRegister existing(String riskCode, String type, String severity, String handleStatus) {
        BizRiskRegister risk = new BizRiskRegister();
        risk.setId(500L);
        risk.setRiskCode(riskCode);
        risk.setRiskType(type);
        risk.setSeverity(severity);
        risk.setHandleStatus(handleStatus);
        risk.setImpactAmount(new BigDecimal("50000"));
        return risk;
    }

    private RiskScanService serviceWith(RiskRule... rules) {
        return new RiskScanService(riskMapper, projectMapper, ownerResolver, List.of(rules));
    }

    @BeforeEach
    void setUp() {
        // 责任人解析默认返回项目经理（部分用例不消费该桩，故用 LENIENT）
        when(ownerResolver.resolveProjectManager(anyLong())).thenReturn(new String[]{"9", "王项目经理"});
    }

    @Nested
    @DisplayName("scan() 台账 upsert 语义")
    class ScanTests {

        @Test
        @DisplayName("正常路径 — 新风险插入 OPEN 并回填责任人（六要素齐备）")
        void scan_newRisk_insertedWithOwner() {
            when(riskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            RiskFinding f = finding("PROFIT_LOSS", 1L, BizRiskRegister.SEVERITY_RED, "PROJECT", 1L);

            Map<String, Object> result = serviceWith(new StubRule("PROFIT_LOSS", List.of(f), false)).scan();

            assertThat(result.get("inserted")).isEqualTo(1);
            assertThat(result.get("findings")).isEqualTo(1);
            ArgumentCaptor<BizRiskRegister> captor = ArgumentCaptor.forClass(BizRiskRegister.class);
            verify(riskMapper).insert(captor.capture());
            BizRiskRegister saved = captor.getValue();
            assertThat(saved.getRiskCode()).isEqualTo("PROFIT_LOSS:1:1");
            assertThat(saved.getHandleStatus()).isEqualTo(BizRiskRegister.HANDLE_OPEN);
            assertThat(saved.getOwnerId()).isEqualTo(9L);
            assertThat(saved.getOwnerName()).isEqualTo("王项目经理");
            assertThat(saved.getLastScanAt()).isNotNull();
        }

        @Test
        @DisplayName("幂等路径 — 同 riskCode 已存在且为 OPEN 时覆盖更新，不重复插入")
        void scan_existingOpenRisk_updatedNotInserted() {
            BizRiskRegister existing = existing("PROFIT_LOSS:1:1", "PROFIT_LOSS",
                    BizRiskRegister.SEVERITY_YELLOW, BizRiskRegister.HANDLE_OPEN);
            when(riskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing));
            RiskFinding f = finding("PROFIT_LOSS", 1L, BizRiskRegister.SEVERITY_YELLOW, "PROJECT", 1L);

            Map<String, Object> result = serviceWith(new StubRule("PROFIT_LOSS", List.of(f), false)).scan();

            assertThat(result.get("inserted")).isEqualTo(0);
            assertThat(result.get("updated")).isEqualTo(1);
            verify(riskMapper, never()).insert(any(BizRiskRegister.class));
            verify(riskMapper, atLeastOnce()).updateById(existing);
            // 影响金额刷新为本轮扫描值
            assertThat(existing.getImpactAmount()).isEqualByComparingTo("100000");
        }

        @Test
        @DisplayName("消音路径 — IGNORED 且判级未升高时仅刷新扫描时间，不重开不覆盖处理状态")
        void scan_ignoredRisk_staysIgnored() {
            BizRiskRegister ignored = existing("BUDGET_OVER:1:1", "BUDGET_OVER",
                    BizRiskRegister.SEVERITY_YELLOW, BizRiskRegister.HANDLE_IGNORED);
            when(riskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ignored);
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            RiskFinding f = finding("BUDGET_OVER", 1L, BizRiskRegister.SEVERITY_YELLOW, "PROJECT", 1L);

            serviceWith(new StubRule("BUDGET_OVER", List.of(f), false)).scan();

            assertThat(ignored.getHandleStatus()).isEqualTo(BizRiskRegister.HANDLE_IGNORED);
            // 消音期间不覆盖严重级别与影响金额（保留人工处置时点的事实）
            assertThat(ignored.getImpactAmount()).isEqualByComparingTo("50000");
            assertThat(ignored.getLastScanAt()).isNotNull();
        }

        @Test
        @DisplayName("升级重开路径 — IGNORED 但判级 YELLOW→RED 时强制重开并留痕原因")
        void scan_ignoredButEscalated_reopened() {
            BizRiskRegister ignored = existing("PROFIT_LOSS:1:1", "PROFIT_LOSS",
                    BizRiskRegister.SEVERITY_YELLOW, BizRiskRegister.HANDLE_IGNORED);
            when(riskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ignored);
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            RiskFinding f = finding("PROFIT_LOSS", 1L, BizRiskRegister.SEVERITY_RED, "PROJECT", 1L);

            serviceWith(new StubRule("PROFIT_LOSS", List.of(f), false)).scan();

            assertThat(ignored.getHandleStatus()).isEqualTo(BizRiskRegister.HANDLE_OPEN);
            assertThat(ignored.getSeverity()).isEqualTo(BizRiskRegister.SEVERITY_RED);
            assertThat(ignored.getHandleNote()).contains("风险升级").contains("YELLOW→RED");
        }

        @Test
        @DisplayName("自动关闭路径 — 本轮未命中的 OPEN/PROCESSING 旧风险置 RESOLVED（风险消失）")
        void scan_riskDisappeared_autoResolved() {
            BizRiskRegister stale = existing("FUND_GAP:1:7", "FUND_GAP",
                    BizRiskRegister.SEVERITY_YELLOW, BizRiskRegister.HANDLE_OPEN);
            when(riskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stale));

            Map<String, Object> result = serviceWith(new StubRule("FUND_GAP", List.of(), false)).scan();

            assertThat(result.get("resolved")).isEqualTo(1);
            assertThat(stale.getHandleStatus()).isEqualTo(BizRiskRegister.HANDLE_RESOLVED);
            assertThat(stale.getHandleNote()).contains("风险条件已消除");
        }

        @Test
        @DisplayName("异常隔离路径 — 单规则抛异常不中断整体扫描，failedRules 如实上报")
        void scan_ruleThrows_isolatedAndReported() {
            when(riskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            RiskFinding ok = finding("FUND_GAP", 2L, BizRiskRegister.SEVERITY_YELLOW, "FORECAST", 8L);
            RiskRule broken = new StubRule("PROFIT_LOSS", List.of(), true);
            RiskRule healthy = new StubRule("FUND_GAP", List.of(ok), false);

            Map<String, Object> result = serviceWith(broken, healthy).scan();

            assertThat(result.get("scannedRules")).isEqualTo(2);
            @SuppressWarnings("unchecked")
            List<String> failed = (List<String>) result.get("failedRules");
            assertThat(failed).containsExactly("PROFIT_LOSS");
            // 失败类型必须被排除出自动关闭范围：否则“执行失败→findings 为空”会被误判为“风险消失”，
            // 导致该类型全部 OPEN 风险被错误 RESOLVED（2026-09-23 审查修复点）
            @SuppressWarnings("unchecked")
            Set<String> skipped = (Set<String>) result.get("skippedAutoCloseTypes");
            assertThat(skipped).containsExactly("PROFIT_LOSS");
            // 健康规则的结果仍入台账
            assertThat(result.get("inserted")).isEqualTo(1);
        }

        @Test
        @DisplayName("公司级风险 riskCode — projectId 为空时用 COMPANY 占位，避免与项目级撞键")
        void scan_companyLevelRisk_codeUsesCompanyPlaceholder() {
            when(riskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            RiskFinding f = finding("FUND_GAP", null, BizRiskRegister.SEVERITY_RED, "FORECAST", 3L);

            serviceWith(new StubRule("FUND_GAP", List.of(f), false)).scan();

            ArgumentCaptor<BizRiskRegister> captor = ArgumentCaptor.forClass(BizRiskRegister.class);
            verify(riskMapper).insert(captor.capture());
            assertThat(captor.getValue().getRiskCode()).isEqualTo("FUND_GAP:COMPANY:3");
        }
    }

    @Nested
    @DisplayName("handle() 处理流转")
    class HandleTests {

        @Test
        @DisplayName("正常路径 — 认领/解决/忽略均记录处理人与处理时间（可审计）")
        void handle_validActions_recordsAuditTrail() {
            BizRiskRegister risk = existing("PROFIT_LOSS:1:1", "PROFIT_LOSS",
                    BizRiskRegister.SEVERITY_RED, BizRiskRegister.HANDLE_OPEN);
            when(riskMapper.selectById(500L)).thenReturn(risk);

            serviceWith().handle(500L, BizRiskRegister.HANDLE_PROCESSING, 9L, "已认领核查");

            assertThat(risk.getHandleStatus()).isEqualTo(BizRiskRegister.HANDLE_PROCESSING);
            assertThat(risk.getHandledBy()).isEqualTo(9L);
            assertThat(risk.getHandledAt()).isNotNull();
            assertThat(risk.getHandleNote()).isEqualTo("已认领核查");
            verify(riskMapper, times(1)).updateById(risk);
        }

        @Test
        @DisplayName("异常路径 — 非法动作被拒绝（不静默按默认值处理）")
        void handle_invalidAction_rejected() {
            assertThatThrownBy(() -> serviceWith().handle(500L, "CLOSED", 9L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("处理动作不合法");
            verify(riskMapper, never()).updateById(any(BizRiskRegister.class));
        }

        @Test
        @DisplayName("异常路径 — 风险记录不存在时抛异常")
        void handle_riskNotFound_rejected() {
            when(riskMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> serviceWith().handle(999L, BizRiskRegister.HANDLE_RESOLVED, 9L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("风险记录不存在");
        }

        @Test
        @DisplayName("状态机守卫 — 已解决风险仅可重开，不可直接置忽略/处理中")
        void handle_resolvedOnlyReopenable() {
            BizRiskRegister resolved = existing("PROFIT_LOSS:1:1", "PROFIT_LOSS",
                    BizRiskRegister.SEVERITY_RED, BizRiskRegister.HANDLE_RESOLVED);
            when(riskMapper.selectById(500L)).thenReturn(resolved);

            assertThatThrownBy(() -> serviceWith().handle(500L, BizRiskRegister.HANDLE_IGNORED, 9L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅可重开");

            serviceWith().handle(500L, BizRiskRegister.HANDLE_OPEN, 9L, "复核后重开");
            assertThat(resolved.getHandleStatus()).isEqualTo(BizRiskRegister.HANDLE_OPEN);
        }
    }

    @Nested
    @DisplayName("summary() 分级汇总")
    class SummaryTests {

        @Test
        @DisplayName("正常路径 — 按级别统计数量与影响金额合计")
        void summary_aggregatesBySeverity() {
            BizRiskRegister red = existing("A", "PROFIT_LOSS", BizRiskRegister.SEVERITY_RED,
                    BizRiskRegister.HANDLE_OPEN);
            red.setImpactAmount(new BigDecimal("3200000"));
            BizRiskRegister yellow1 = existing("B", "BUDGET_OVER", BizRiskRegister.SEVERITY_YELLOW,
                    BizRiskRegister.HANDLE_OPEN);
            yellow1.setImpactAmount(new BigDecimal("1800000"));
            BizRiskRegister yellow2 = existing("C", "FUND_GAP", BizRiskRegister.SEVERITY_YELLOW,
                    BizRiskRegister.HANDLE_PROCESSING);
            yellow2.setImpactAmount(new BigDecimal("260000"));
            when(riskMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(red, yellow1, yellow2));

            Map<String, Object> result = serviceWith().summary(null);

            assertThat(result.get("redCount")).isEqualTo(1L);
            assertThat((BigDecimal) result.get("redImpact")).isEqualByComparingTo("3200000");
            assertThat(result.get("yellowCount")).isEqualTo(2L);
            assertThat((BigDecimal) result.get("yellowImpact")).isEqualByComparingTo("2060000");
            assertThat(result.get("infoCount")).isEqualTo(0L);
            assertThat(result.get("activeTotal")).isEqualTo(3);
        }

        @Test
        @DisplayName("边界路径 — 无活跃风险时各级别计数为 0、金额为 0（不返回 null）")
        void summary_noRisks_zeroed() {
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            Map<String, Object> result = serviceWith().summary(null);

            assertThat(result.get("redCount")).isEqualTo(0L);
            assertThat((BigDecimal) result.get("redImpact")).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.get("activeTotal")).isEqualTo(0);
        }
    }
}
