# M3 真实数据叙事实施记录

## 实施范围
1. **P2-A 成本五态数据叙事适配器与组件**：
   - 双端纯函数 `costNarrative.ts`（PC 与移动端严格统一公式口径）
   - 基准(BASE) → 当前(CURR) → 承诺(COMM) → 实际(ACTL) → 完工预测(EAC)
   - 严格落实 `remainingBudget = currentTotal - actualTotal` 与真实 `varianceAmount`
   - 月度趋势为空时严格展示 `ZwBlueprintEmpty type="trend"`，拒绝任何前端插值伪造
   - 双端呈现组件 `ZwCostFiveState.vue`
2. **P2-B 项目全生命周期阶段图（当前视图）**：
   - 双端 `ZwLifecycleRail.vue`，展示立项 → 合同 → 方案 → 履约 → 结算 → 归档实时状态快照
   - 不生成任何未经验证的历史阶段完成时间戳
3. **P2-C 审批凭证化**：
   - 双端 `ZwCredentialPanel.vue`，形成结构化业务单据，带“流程状态”专属印章与真实记录时间线
4. **P2-D 甘特图 WBS 工业图例**：
   - PC 端 `ZwScheduleLegend.vue`，关键路径、延误条纹、基准虚线与里程碑菱形双重编码

## 验证结果
- PC 端 `src/__tests__/narrative.test.ts` 全部通过（3/3 tests passed）。
- 移动端 `tests/costNarrative.test.ts` 全部通过（2/2 tests passed）。
