package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.mapper.BizRiskRegisterMapper;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.mapper.BizReceivableMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.SysUserProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 项目台账首屏聚合（S3.1）。
 *
 * <p>一次返回项目总数、我的项目数、在建数、预警数、活跃风险数、逾期应收，以及当前页项目的
 * 健康度/风险数/逾期应收。项目范围由 {@link BizProjectMapper} 的 {@code @DataPermission}
 * 控制；风险与应收按项目 ID 归属，不另做前端拼接。</p>
 *
 * <p>同时实现 {@link ProjectWarningIdProvider}：把「健康度 RED/YELLOW ∪ 活跃风险」的预警
 * ID 集合下推给 project 模块的 view=WARNING 分页，保证预警卡数字与列表行数一致。
 * 健康度依赖利润预测，计算昂贵，结果按用户做 60 秒短缓存（同一轮翻页复用）。</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectWorkbenchService implements com.zwinsight.project.spi.ProjectWarningIdProvider {

    /** 预警 ID 缓存时长：分钟级新鲜度足够，避免每次翻页重算 2N 次预测查询 */
    private static final long WARNING_TTL_MS = 60_000L;
    private static final int WARNING_CACHE_MAX = 200;

    private final BizProjectMapper projectMapper;
    private final BizRiskRegisterMapper riskMapper;
    private final BizReceivableMapper receivableMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final CockpitService cockpitService;

    /** key = tenantId:userId（数据权限随用户变化）；value = 预警项目 ID 集合 + 过期时间 */
    private final Map<String, WarningCacheEntry> warningCache = new ConcurrentHashMap<>();

    private record WarningCacheEntry(Set<Long> ids, long expiresAt) {
    }

    @Override
    public Set<Long> warningProjectIds() {
        String key = SecurityContextHolder.getTenantId() + ":" + SecurityContextHolder.getUserId();
        long now = System.currentTimeMillis();
        WarningCacheEntry cached = warningCache.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return cached.ids();
        }
        Set<Long> ids = computeWarningProjectIds();
        warningCache.put(key, new WarningCacheEntry(ids, now + WARNING_TTL_MS));
        if (warningCache.size() > WARNING_CACHE_MAX) {
            warningCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }
        return ids;
    }

    public Map<String, Object> summary(String projectIds) {
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<>());
        List<Map<String, Object>> healthRows = cockpitService.getProjectHealth();

        // 活跃风险（OPEN/PROCESSING）；riskMapper 无数据权限注解，按已授权项目 ID 显式收口
        Set<Long> scopedProjectIds = projects.stream()
                .map(BizProject::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        long riskOpen = 0L;
        Set<Long> riskProjectIds = new HashSet<>();
        List<BizRiskRegister> activeRisks = activeRisks(scopedProjectIds);
        riskOpen = activeRisks.size();
        for (BizRiskRegister risk : activeRisks) {
            if (risk.getProjectId() != null) {
                riskProjectIds.add(risk.getProjectId());
            }
        }

        // 逾期应收：未结清且到期日早于今天，余额按已核销金额扣减
        List<BizReceivable> overdueReceivables = receivableMapper.selectList(
                new LambdaQueryWrapper<BizReceivable>()
                        .eq(BizReceivable::getStatus, "OPEN")
                        .lt(BizReceivable::getDueDate, LocalDate.now())
                        .apply("(receivable_amount - COALESCE(written_off_amount, 0)) > 0"));
        Map<Long, BigDecimal> overdueByProject = new HashMap<>();
        Map<Long, Long> overdueCountByProject = new HashMap<>();
        BigDecimal overdueAmount = BigDecimal.ZERO;
        for (BizReceivable receivable : overdueReceivables) {
            BigDecimal openAmount = nvl(receivable.getReceivableAmount())
                    .subtract(nvl(receivable.getWrittenOffAmount()));
            overdueAmount = overdueAmount.add(openAmount);
            if (receivable.getProjectId() != null) {
                overdueByProject.merge(receivable.getProjectId(), openAmount, BigDecimal::add);
                overdueCountByProject.merge(receivable.getProjectId(), 1L, Long::sum);
            }
        }

        List<Map<String, Object>> rows = filterRows(healthRows, projectIds);
        for (Map<String, Object> row : rows) {
            Long projectId = toLong(row.get("projectId"));
            row.put("overdueReceivableAmount", overdueByProject.getOrDefault(projectId, BigDecimal.ZERO));
            row.put("overdueReceivableCount", overdueCountByProject.getOrDefault(projectId, 0L));
            Object topRisks = row.get("topRisks");
            row.put("topRisk", topRisks instanceof List<?> list && !list.isEmpty() ? list.get(0) : null);
        }

        // 预警口径（与 view=WARNING 同源）：健康度 RED/YELLOW ∪ 存在 OPEN/PROCESSING 风险，
        // 既覆盖亏损无风险单的项目，也保证卡片数字 = 点进列表的行数
        Set<Long> warningProjectIds = unionWarningIds(healthRows, riskProjectIds);
        primeWarningCache(warningProjectIds);
        long warning = warningProjectIds.size();
        long inConstruction = projects.stream()
                .filter(project -> "CONSTRUCTION".equals(project.getStatus()))
                .count();
        // 「我的项目」= 当前用户参与且在当前数据范围内的项目（与 view=MINE 同一口径）
        long mine = 0L;
        Long currentUserId = SecurityContextHolder.getUserId();
        if (currentUserId != null && !scopedProjectIds.isEmpty()) {
            mine = userProjectMapper.selectProjectIdsByUserId(currentUserId).stream()
                    .filter(scopedProjectIds::contains)
                    .distinct()
                    .count();
        }
        Map<String, Long> statusDistribution = projects.stream()
                .collect(Collectors.groupingBy(
                        project -> project.getStatus() == null ? "UNKNOWN" : project.getStatus(),
                        LinkedHashMap::new,
                        Collectors.counting()));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", projects.size());
        summary.put("mine", mine);
        summary.put("inConstruction", inConstruction);
        summary.put("warning", warning);
        summary.put("riskOpen", riskOpen);
        summary.put("overdueReceivableCount", overdueReceivables.size());
        summary.put("overdueReceivableAmount", overdueAmount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("statusDistribution", statusDistribution);
        result.put("projects", rows);
        return result;
    }

    /**
     * 只过滤健康度行，不影响 summary 的全量口径。
     * 参数存在但没有合法 ID 时返回空列表，避免调用方以为已过滤却看到全量。
     */
    private static List<Map<String, Object>> filterRows(List<Map<String, Object>> rows, String projectIds) {
        if (projectIds == null || projectIds.isBlank()) {
            return new ArrayList<>(rows);
        }
        Set<Long> ids = Arrays.stream(projectIds.split(","))
                .map(String::trim)
                .filter(value -> value.matches("\\d+"))
                .map(Long::valueOf)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> ids.contains(toLong(row.get("projectId"))))
                .toList();
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && text.matches("\\d+")) return Long.valueOf(text);
        return null;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 活跃风险（OPEN/PROCESSING），按已授权项目范围收口；范围为空时不查库 */
    private List<BizRiskRegister> activeRisks(Set<Long> scopedProjectIds) {
        if (scopedProjectIds.isEmpty()) {
            return List.of();
        }
        return riskMapper.selectList(new LambdaQueryWrapper<BizRiskRegister>()
                .in(BizRiskRegister::getProjectId, scopedProjectIds)
                .in(BizRiskRegister::getHandleStatus,
                        BizRiskRegister.HANDLE_OPEN, BizRiskRegister.HANDLE_PROCESSING));
    }

    /** 预警项目 ID = 健康度非 GREEN ∪ 活跃风险项目 */
    private static Set<Long> unionWarningIds(List<Map<String, Object>> healthRows, Set<Long> riskProjectIds) {
        Set<Long> ids = new HashSet<>(riskProjectIds);
        for (Map<String, Object> row : healthRows) {
            if ("GREEN".equals(String.valueOf(row.get("health")))) {
                continue;
            }
            Long projectId = toLong(row.get("projectId"));
            if (projectId != null) {
                ids.add(projectId);
            }
        }
        return ids;
    }

    /** 独立计算预警 ID（缓存未命中时）：项目范围、风险、健康度各查一次 */
    private Set<Long> computeWarningProjectIds() {
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<>());
        Set<Long> scopedProjectIds = projects.stream()
                .map(BizProject::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> riskProjectIds = activeRisks(scopedProjectIds).stream()
                .map(BizRiskRegister::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> ids = unionWarningIds(cockpitService.getProjectHealth(), riskProjectIds);
        ids.retainAll(scopedProjectIds);
        return ids;
    }

    /** summary 已算出同一集合时直接写入缓存，避免预警列表随后翻页再算一次 */
    private void primeWarningCache(Set<Long> warningIds) {
        String key = SecurityContextHolder.getTenantId() + ":" + SecurityContextHolder.getUserId();
        warningCache.put(key, new WarningCacheEntry(Set.copyOf(warningIds),
                System.currentTimeMillis() + WARNING_TTL_MS));
    }
}
