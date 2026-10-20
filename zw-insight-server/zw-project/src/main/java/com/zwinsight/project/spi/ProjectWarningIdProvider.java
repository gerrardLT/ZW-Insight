package com.zwinsight.project.spi;

import java.util.Set;

/**
 * 「预警项目」ID 提供者（SPI）。
 *
 * <p>预警口径 = 健康度 RED/YELLOW ∪ 存在 OPEN/PROCESSING 风险，其中健康度依赖
 * 利润预测（合同收入 − 预计总成本），属于经营驾驶舱能力，无法在 project 模块用 SQL 复算。
 * 因此由具备该能力的模块（zw-dashboard）实现本接口并注册为 Bean，project 侧只做 ID 过滤，
 * 保证「预警卡数字 = 点进列表的行数」。</p>
 *
 * <p>实现方需自行处理数据权限与缓存；未装配实现时，page(view=WARNING) 返回空列表而不是全量。</p>
 */
public interface ProjectWarningIdProvider {

    /**
     * 当前用户可见范围内的预警项目 ID 集合（可能为空，不会返回 null）。
     */
    Set<Long> warningProjectIds();
}
