package com.zwinsight.material.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.domain.BizStockWarningConfig;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import com.zwinsight.material.mapper.BizStockWarningConfigMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
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
    private final RedisUtils redisUtils;

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

            // 发送预警通知
            sendWarning(stock, level, safetyStock);

            // 标记已发送
            redisUtils.set(redisKey, "1", KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);
            warningCount++;
        }

        log.info("库存预警任务完成, 发送通知{}条", warningCount);
    }

    private void sendWarning(BizProjectMaterialStock stock, String level, BigDecimal safetyStock) {
        String projectName = getProjectName(stock.getProjectId());
        String levelDesc = LEVEL_ZERO.equals(level) ? "零库存" : "低库存";
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

        // TODO: 集成 MessageService 发送站内信给项目材料管理员
        log.info("【库存预警】{}", content);
    }

    private String getProjectName(Long projectId) {
        if (projectId == null) return "未知项目";
        BizProject project = projectMapper.selectById(projectId);
        return project != null ? project.getProjectName() : "未知项目";
    }
}
