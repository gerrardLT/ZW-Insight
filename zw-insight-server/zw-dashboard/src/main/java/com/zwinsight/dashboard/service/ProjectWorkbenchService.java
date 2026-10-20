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
import java.util.stream.Collectors;

/**
 * 项目台账首屏聚合（S3.1）。
 *
 * <p>一次返回项目总数、我的项目数、在建数、预警数、活跃风险数、逾期应收，以及当前页项目的
 * 健康度/风险数/逾期应收。项目范围由 {@link BizProjectMapper} 的 {@code @DataPermission}
 * 控制；风险与应收按项目 ID 归属，不另做前端拼接。</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectWorkbenchService {

    private final BizProjectMapper projectMapper;
    private final BizRiskRegisterMapper riskMapper;
    private final BizReceivableMapper receivableMapper;
    private final SysUserProjectMapper userProjectMapper;
    private final CockpitService cockpitService;

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
        if (!scopedProjectIds.isEmpty()) {
            List<BizRiskRegister> activeRisks = riskMapper.selectList(new LambdaQueryWrapper<BizRiskRegister>()
                    .in(BizRiskRegister::getProjectId, scopedProjectIds)
                    .in(BizRiskRegister::getHandleStatus,
                            BizRiskRegister.HANDLE_OPEN, BizRiskRegister.HANDLE_PROCESSING));
            riskOpen = activeRisks.size();
            for (BizRiskRegister risk : activeRisks) {
                if (risk.getProjectId() != null) {
                    riskProjectIds.add(risk.getProjectId());
                }
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

        // 预警口径与 view=RISK 同源（存在 OPEN/PROCESSING 风险的项目），保证卡片数字点进去一致；
        // 仅「预计利润为负」而无风险单的项目不进预警计数（健康度列仍会显示 RED）
        long warning = riskProjectIds.size();
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
}
