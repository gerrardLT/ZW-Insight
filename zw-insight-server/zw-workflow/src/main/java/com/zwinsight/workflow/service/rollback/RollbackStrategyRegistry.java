package com.zwinsight.workflow.service.rollback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 回滚策略注册表
 * <p>
 * 自动收集所有实现了 RollbackStrategy 接口的 Bean，
 * 按 bizType 索引，供回滚服务快速查找。
 * </p>
 */
@Slf4j
@Component
public class RollbackStrategyRegistry {

    private final Map<String, RollbackStrategy> strategyMap = new ConcurrentHashMap<>();

    /**
     * 通过构造器注入自动注册所有策略
     */
    public RollbackStrategyRegistry(List<RollbackStrategy> strategies) {
        for (RollbackStrategy strategy : strategies) {
            strategyMap.put(strategy.getBizType(), strategy);
            log.info("注册回滚策略: bizType={}, class={}", strategy.getBizType(), strategy.getClass().getSimpleName());
        }
        log.info("回滚策略注册完成，共 {} 个策略", strategyMap.size());
    }

    /**
     * 根据业务类型获取回滚策略
     *
     * @param bizType 业务类型
     * @return 对应的回滚策略，不存在返回 null
     */
    public RollbackStrategy getStrategy(String bizType) {
        return strategyMap.get(bizType);
    }

    /**
     * 检查是否存在指定类型的策略
     */
    public boolean hasStrategy(String bizType) {
        return strategyMap.containsKey(bizType);
    }
}
