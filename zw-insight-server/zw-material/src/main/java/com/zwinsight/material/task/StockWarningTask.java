package com.zwinsight.material.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.domain.BizStockWarningConfig;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.material.mapper.BizStockWarningConfigMapper;
import com.zwinsight.message.service.MessageService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.BizProjectMember;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectMemberMapper;
import com.zwinsight.security.service.TenantTaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 库存预警定时任务
 * <p>
 * 每日 09:00 执行，扫描所有项目的材料库存记录，
 * 对比安全库存阈值配置，低于阈值时发送预警通知。
 * </p>
 * <p>
 * 预警规则：
 * <ul>
 *   <li>低库存预警：stockQuantity <= safetyStock</li>
 *   <li>零库存预警：stockQuantity <= 0</li>
 *   <li>去重机制：同一材料同一级别 7 天内不重复通知（Redis key 过期）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockWarningTask {

    private final BizProjectMaterialStockMapper stockMapper;
    private final BizStockWarningConfigMapper configMapper;
    private final BizProjectMapper projectMapper;
    private final BizProjectMemberMapper projectMemberMapper;
    private final MessageService messageService;
    private final RedisUtils redisUtils;
    private final TenantTaskRunner tenantTaskRunner;

    private static final String KEY_PREFIX = "stock:warning:";
    private static final long KEY_EXPIRE_SECONDS = 7 * 24 * 60 * 60; // 7天去重

    public static final String LEVEL_ZERO = "ZERO_STOCK";
    public static final String LEVEL_LOW = "LOW_STOCK";

    /**
     * 每日 09:00 执行库存预警扫描
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void execute() {
        log.info("库存预警定时任务开始执行");
        // 逐租户设置上下文执行（拦截器增强后跨租户任务必须逐租户执行）
        tenantTaskRunner.runForActiveTenants("库存预警", tenantId -> doExecute());
    }

    void doExecute() {
        // 1. 加载所有安全库存配置（按 projectId + materialId 唯一）
        List<BizStockWarningConfig> configs = configMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, BigDecimal> safetyStockMap = configs.stream()
                .collect(Collectors.toMap(
                        c -> c.getProjectId() + ":" + c.getMaterialId(),
                        BizStockWarningConfig::getSafetyStock,
                        (a, b) -> a));

        // 2. 如果没有配置，使用全局默认阈值
        BigDecimal globalDefault = configs.stream()
                .filter(c -> c.getProjectId() == null || c.getProjectId() == 0)
                .findFirst()
                .map(BizStockWarningConfig::getSafetyStock)
                .orElse(BigDecimal.TEN); // 默认安全库存 10

        // 3. 扫描所有库存记录
        List<BizProjectMaterialStock> allStocks = stockMapper.selectList(new LambdaQueryWrapper<>());
        int warningCount = 0;
        int failCount = 0;

        for (BizProjectMaterialStock stock : allStocks) {
            BigDecimal currentQty = stock.getStockQuantity() != null ? stock.getStockQuantity() : BigDecimal.ZERO;

            // 获取该材料的安全库存阈值
            String configKey = stock.getProjectId() + ":" + stock.getMaterialId();
            BigDecimal safetyStock = safetyStockMap.getOrDefault(configKey, globalDefault);

            String level = null;
            if (currentQty.compareTo(BigDecimal.ZERO) <= 0) {
                level = LEVEL_ZERO;
            } else if (currentQty.compareTo(safetyStock) <= 0) {
                level = LEVEL_LOW;
            }

            if (level == null) continue;

            // 去重检查
            String redisKey = KEY_PREFIX + stock.getProjectId() + ":" + stock.getMaterialId() + ":" + level;
            if (Boolean.TRUE.equals(redisUtils.hasKey(redisKey))) {
                continue;
            }

            // 发送预警通知（失败时记录并不标记去重键，下次扫描重试；单条失败不中断整体扫描）
            try {
                sendWarning(stock, level, safetyStock);
            } catch (Exception e) {
                failCount++;
                log.error("库存预警通知发送失败, projectId={}, materialId={}, level={}",
                        stock.getProjectId(), stock.getMaterialId(), level, e);
                continue;
            }

            // 发送成功后才标记去重
            redisUtils.set(redisKey, "1", KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);
            warningCount++;
        }

        log.info("库存预警任务完成, 发送通知{}条, 失败{}条", warningCount, failCount);
    }

    /**
     * 发送库存预警站内信（P0 Req4.7）：
     * 收件人优先项目材料员（MATERIAL_OFFICER），无材料员时兜底项目经理；
     * 消息服务不可用或无收件人时抛出异常，由调用方记录失败，不静默降级。
     */
    void sendWarning(BizProjectMaterialStock stock, String level, BigDecimal safetyStock) {
        String projectName = getProjectName(stock.getProjectId());
        String levelDesc = LEVEL_ZERO.equals(level) ? "零库存" : "低库存";
        String title = String.format("【%s】材料库存预警", levelDesc);
        String content = String.format(
                "项目【%s】材料【%s(%s)】%s预警：当前库存 %.2f %s，安全库存阈值 %.2f %s，请及时补充。",
                projectName,
                stock.getMaterialName(),
                stock.getSpecification() != null ? stock.getSpecification() : "",
                levelDesc,
                stock.getStockQuantity(),
                stock.getUnit() != null ? stock.getUnit() : "",
                safetyStock,
                stock.getUnit() != null ? stock.getUnit() : ""
        );

        List<Long> receiverIds = resolveReceiverIds(stock.getProjectId());
        if (receiverIds.isEmpty()) {
            throw new BusinessException(String.format(
                    "项目[%s]未配置材料员或项目经理，库存预警无收件人", projectName));
        }
        for (Long receiverId : receiverIds) {
            messageService.sendMessage(receiverId, title, content,
                    "WARNING", "STOCK_WARNING", stock.getId());
        }
        log.info("【库存预警】已发送站内信{}条: {}", receiverIds.size(), content);
    }

    /**
     * 解析预警收件人：材料员优先，无材料员时兜底项目经理
     */
    private List<Long> resolveReceiverIds(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        List<Long> officers = selectMemberIdsByRole(projectId, "MATERIAL_OFFICER");
        if (!officers.isEmpty()) {
            return officers;
        }
        return selectMemberIdsByRole(projectId, "PROJECT_MANAGER");
    }

    private List<Long> selectMemberIdsByRole(Long projectId, String roleCode) {
        LambdaQueryWrapper<BizProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMember::getProjectId, projectId)
                .eq(BizProjectMember::getStatus, 1)
                .apply("JSON_CONTAINS(project_roles, '\"" + roleCode + "\"')");
        return projectMemberMapper.selectList(wrapper).stream()
                .map(BizProjectMember::getUserId)
                .distinct()
                .collect(Collectors.toList());
    }

    private String getProjectName(Long projectId) {
        if (projectId == null) return "未知项目";
        BizProject project = projectMapper.selectById(projectId);
        return project != null ? project.getProjectName() : "未知项目";
    }
}
