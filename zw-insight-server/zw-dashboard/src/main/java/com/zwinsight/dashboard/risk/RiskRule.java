package com.zwinsight.dashboard.risk;

import java.util.List;

/**
 * 风险规则策略接口（驾驶舱 V1 §15：状态必须由规则自动产生）
 * <p>
 * 每条规则一个实现类（类名带规则前缀，规避组件扫描短类名冲突——AGENTS 跨模块级联约束同源教训）。
 * 实现约束：
 * 1) evaluate 只读扫描，不产生业务副作用；
 * 2) 阈值走 @Value 配置，不硬编码魔数；
 * 3) 单条数据异常不得中断整体扫描（RiskScanService 逐规则隔离异常）。
 * </p>
 */
public interface RiskRule {

    /**
     * 规则类型标识（与 RiskFinding.riskType 一致，用于台账幂等键与筛选）
     */
    String riskType();

    /**
     * 扫描并产出当前时点的全部风险条目
     */
    List<RiskFinding> evaluate();
}
