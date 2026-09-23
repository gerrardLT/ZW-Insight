package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.mapper.BizRiskRegisterMapper;
import com.zwinsight.dashboard.risk.ProjectOwnerResolver;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.risk.RiskRule;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 风险扫描与台账服务（V2026_59，驾驶舱 2B 风险中心）
 * <p>
 * 扫描语义（RiskScanTask 每日驱动，也可手动触发）：
 * 1) 逐规则执行 evaluate（单规则异常隔离，不中断整体，错误如实记录）；
 * 2) 命中风险按 riskCode 幂等 upsert：新风险 OPEN；已 IGNORED 的保持消音，
 *    但规则判级升高（如 YELLOW→RED）时强制重开（重大风险不允许被永久忽略）；
 * 3) 本轮未命中且状态为 OPEN/PROCESSING 的同类型旧风险自动 RESOLVED（风险消失）；
 * 4) 责任人取项目 PROJECT_MANAGER（六要素之"谁负责"，缺失时如实留空）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskScanService {

    private final BizRiskRegisterMapper riskMapper;
    private final BizProjectMapper projectMapper;
    private final ProjectOwnerResolver ownerResolver;
    private final List<RiskRule> rules;

    private static final Set<String> VALID_HANDLE_ACTIONS =
            Set.of(BizRiskRegister.HANDLE_PROCESSING, BizRiskRegister.HANDLE_RESOLVED,
                    BizRiskRegister.HANDLE_IGNORED, BizRiskRegister.HANDLE_OPEN);

    private static final Map<String, Integer> SEVERITY_RANK =
            Map.of(BizRiskRegister.SEVERITY_INFO, 0, BizRiskRegister.SEVERITY_YELLOW, 1,
                    BizRiskRegister.SEVERITY_RED, 2);

    /**
     * 执行全量风险扫描（所有规则）
     * <p><b>自动关闭仅限本轮成功评估的规则类型</b>：规则抛异常时其 findings 为空，
     * 若把该类型也纳入自动关闭范围，会把因执行失败而“未命中”误判为“风险消失”，
     * 批量关闭旧风险（2026-09-23 审查发现的缺陷）；失败类型的旧风险保持原状待下次扫描。</p>
     *
     * @return {scannedRules, findings, inserted, updated, resolved, failedRules, skippedAutoCloseTypes}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scan() {
        LocalDateTime now = LocalDateTime.now();
        int findingsCount = 0;
        int inserted = 0;
        int updated = 0;
        int resolved = 0;
        List<String> failedRules = new ArrayList<>();
        Set<String> activeCodes = new HashSet<>();
        Set<String> autoCloseTypes = new HashSet<>();
        Set<String> skippedAutoCloseTypes = new HashSet<>();

        for (RiskRule rule : rules) {
            List<RiskFinding> findings;
            try {
                findings = rule.evaluate();
            } catch (Exception e) {
                // 单规则失败不中断整体扫描，但如实上报（不静默）；
                // 同时排除出自动关闭范围，避免“执行失败”被当成“风险消失”
                failedRules.add(rule.riskType());
                skippedAutoCloseTypes.add(rule.riskType());
                log.error("风险规则执行失败（本轮不做自动关闭）, ruleType={}", rule.riskType(), e);
                continue;
            }
            autoCloseTypes.add(rule.riskType());
            for (RiskFinding finding : findings) {
                findingsCount++;
                activeCodes.add(finding.riskCode());
                if (upsertFinding(finding, now)) {
                    inserted++;
                } else {
                    updated++;
                }
            }
        }

        // 风险消失自动 RESOLVED（仅 OPEN/PROCESSING 且所属规则本轮成功评估；
        // IGNORED 为人工消音不自动变更）
        for (String type : autoCloseTypes) {
            List<BizRiskRegister> openRisks = riskMapper.selectList(new LambdaQueryWrapper<BizRiskRegister>()
                    .eq(BizRiskRegister::getRiskType, type)
                    .in(BizRiskRegister::getHandleStatus,
                            BizRiskRegister.HANDLE_OPEN, BizRiskRegister.HANDLE_PROCESSING));
            for (BizRiskRegister risk : openRisks) {
                if (!activeCodes.contains(risk.getRiskCode())) {
                    risk.setHandleStatus(BizRiskRegister.HANDLE_RESOLVED);
                    risk.setHandledAt(now);
                    risk.setHandleNote("风险条件已消除，扫描自动关闭");
                    risk.setLastScanAt(now);
                    riskMapper.updateById(risk);
                    resolved++;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("scannedRules", rules.size());
        result.put("findings", findingsCount);
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("resolved", resolved);
        result.put("failedRules", failedRules);
        result.put("skippedAutoCloseTypes", skippedAutoCloseTypes);
        log.info("风险扫描完成: {}", result);
        return result;
    }

    /**
     * 分页查询风险台账（severity/handleStatus/riskType/projectId 可选筛选）
     * <p>排序在 SQL 层完成（严重级别降序 → 影响金额降序）：
     * 应用层排序只能影响当前页，会造成跨页顺序错乱（第 2 页的 RED 排在第 1 页的 YELLOW 之后），
     * 故用 MySQL FIELD() 将 severity 映射为排序权重（2026-09-23 审查发现的缺陷）。</p>
     */
    public PageResult<BizRiskRegister> page(int page, int size, String severity,
                                            String handleStatus, String riskType, Long projectId) {
        Page<BizRiskRegister> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizRiskRegister> wrapper = new LambdaQueryWrapper<BizRiskRegister>()
                .eq(severity != null && !severity.isBlank(), BizRiskRegister::getSeverity, severity)
                .eq(handleStatus != null && !handleStatus.isBlank(), BizRiskRegister::getHandleStatus, handleStatus)
                .eq(riskType != null && !riskType.isBlank(), BizRiskRegister::getRiskType, riskType)
                .eq(projectId != null, BizRiskRegister::getProjectId, projectId)
                // 严重级别排序必须在 SQL 层（应用层排序只影响当前页，跨页会错乱）。
                // LambdaQueryWrapper 无 String 列名重载、无法直接表达 FIELD()，故用 last 追加常量 ORDER BY；
                // 分页插件将 LIMIT 追加在此之后，语法正确（常量无注入风险）。
                .last("ORDER BY FIELD(severity,'RED','YELLOW','INFO'), impact_amount DESC");
        Page<BizRiskRegister> result = riskMapper.selectPage(pageParam, wrapper);
        fillProjectNames(result.getRecords());
        return PageResult.of(result);
    }

    /**
     * 风险详情（移动端详情页/穿透入口使用，可独立刷新）
     *
     * @param id 风险台账ID
     * @return 风险记录（已回填项目名）；不存在时抛业务异常，不返回 null
     */
    public BizRiskRegister getById(Long id) {
        BizRiskRegister risk = riskMapper.selectById(id);
        if (risk == null) {
            throw new BusinessException("风险记录不存在: " + id);
        }
        fillProjectNames(List.of(risk));
        return risk;
    }

    /**
     * 风险分级汇总（驾驶舱 §11：数量 + 影响金额，仅统计未关闭风险 OPEN/PROCESSING）
     */
    public Map<String, Object> summary(Long projectId) {
        List<BizRiskRegister> active = riskMapper.selectList(new LambdaQueryWrapper<BizRiskRegister>()
                .eq(projectId != null, BizRiskRegister::getProjectId, projectId)
                .in(BizRiskRegister::getHandleStatus,
                        BizRiskRegister.HANDLE_OPEN, BizRiskRegister.HANDLE_PROCESSING));
        Map<String, Object> result = new HashMap<>();
        for (String severity : List.of(BizRiskRegister.SEVERITY_RED,
                BizRiskRegister.SEVERITY_YELLOW, BizRiskRegister.SEVERITY_INFO)) {
            long count = active.stream().filter(r -> severity.equals(r.getSeverity())).count();
            BigDecimal impact = active.stream()
                    .filter(r -> severity.equals(r.getSeverity()))
                    .map(r -> r.getImpactAmount() != null ? r.getImpactAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(severity.toLowerCase() + "Count", count);
            result.put(severity.toLowerCase() + "Impact", impact);
        }
        result.put("activeTotal", active.size());
        return result;
    }

    /**
     * 风险处理流转（认领 PROCESSING / 解决 RESOLVED / 忽略 IGNORED / 重开 OPEN）。
     * <p>处理人与处理时间强制记录（六要素之"当前处理状态"可审计）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String action, Long handledBy, String handleNote) {
        if (action == null || !VALID_HANDLE_ACTIONS.contains(action)) {
            throw new BusinessException("处理动作不合法，可选值: " + VALID_HANDLE_ACTIONS);
        }
        BizRiskRegister risk = riskMapper.selectById(id);
        if (risk == null) {
            throw new BusinessException("风险记录不存在: " + id);
        }
        if (BizRiskRegister.HANDLE_RESOLVED.equals(risk.getHandleStatus())
                && !BizRiskRegister.HANDLE_OPEN.equals(action)) {
            throw new BusinessException("已解决的风险仅可重开，不可直接置为 " + action);
        }
        risk.setHandleStatus(action);
        risk.setHandledBy(handledBy);
        risk.setHandledAt(LocalDateTime.now());
        risk.setHandleNote(handleNote);
        riskMapper.updateById(risk);
        log.info("风险处理流转, riskId={}, action={}, handledBy={}", id, action, handledBy);
    }

    // ==================== 私有方法 ====================

    /**
     * 按 riskCode 幂等 upsert 单条风险
     *
     * @return true=新插入，false=更新既有
     */
    private boolean upsertFinding(RiskFinding finding, LocalDateTime now) {
        BizRiskRegister existing = riskMapper.selectOne(new LambdaQueryWrapper<BizRiskRegister>()
                .eq(BizRiskRegister::getRiskCode, finding.riskCode()));
        if (existing == null) {
            BizRiskRegister risk = new BizRiskRegister();
            risk.setRiskCode(finding.riskCode());
            risk.setRiskType(finding.riskType());
            risk.setProjectId(finding.projectId());
            risk.setSeverity(finding.severity());
            risk.setTitle(finding.title());
            risk.setImpactAmount(finding.impactAmount());
            risk.setReasonDetail(finding.reasonDetail());
            risk.setNextAction(finding.nextAction());
            risk.setBizRefType(finding.bizRefType());
            risk.setBizRefId(finding.bizRefId());
            risk.setRuleParams(finding.ruleParams());
            risk.setHandleStatus(BizRiskRegister.HANDLE_OPEN);
            risk.setLastScanAt(now);
            fillOwner(risk);
            riskMapper.insert(risk);
            return true;
        }
        boolean ignored = BizRiskRegister.HANDLE_IGNORED.equals(existing.getHandleStatus());
        boolean severityEscalated = SEVERITY_RANK.getOrDefault(finding.severity(), 0)
                > SEVERITY_RANK.getOrDefault(existing.getSeverity(), 0);
        if (ignored && !severityEscalated) {
            // 人工消音且未升级：仅刷新扫描时间，不重开不覆盖处理状态
            existing.setLastScanAt(now);
            riskMapper.updateById(existing);
            return false;
        }
        if (ignored) {
            // 判级升高强制重开（重大风险不允许被永久忽略），留痕原处理信息
            existing.setHandleStatus(BizRiskRegister.HANDLE_OPEN);
            existing.setHandleNote("原被忽略，因风险升级(" + existing.getSeverity() + "→"
                    + finding.severity() + ")自动重开");
        }
        existing.setSeverity(finding.severity());
        existing.setTitle(finding.title());
        existing.setImpactAmount(finding.impactAmount());
        existing.setReasonDetail(finding.reasonDetail());
        existing.setNextAction(finding.nextAction());
        existing.setRuleParams(finding.ruleParams());
        existing.setLastScanAt(now);
        if (existing.getOwnerId() == null) {
            fillOwner(existing);
        }
        riskMapper.updateById(existing);
        return false;
    }

    private void fillOwner(BizRiskRegister risk) {
        String[] owner = ownerResolver.resolveProjectManager(risk.getProjectId());
        if (owner != null) {
            risk.setOwnerId(owner[0] != null ? Long.valueOf(owner[0]) : null);
            risk.setOwnerName(owner[1]);
        }
    }

    private void fillProjectNames(List<BizRiskRegister> records) {
        for (BizRiskRegister risk : records) {
            if (risk.getProjectId() == null) {
                continue;
            }
            BizProject project = projectMapper.selectById(risk.getProjectId());
            if (project != null) {
                risk.setProjectName(project.getProjectName());
            }
        }
    }
}
