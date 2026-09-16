# ZW 工程视觉资产与数据叙事专项 - M0 基线与范围边界

- 建立时间：2026-09-14
- 对应分支：main
- 工作原则：真实接口、真实流程、无静默回退、严禁假数据；不触碰未跟踪的 ui-evolution-demo/

## 1. 现有设计底座现状
- PC 端：
  - 核心色彩：安全橙 (`#ff6b00`)、石墨深色 (`#14161a` ~ `#1e222a`)、混凝土白 (`#f6f7f5`)
  - 样式 Token：`zw-insight-web/src/styles/tokens/{base,light,dark}.css`
  - 图标：基础使用 Tabler 图标映射，工程图标仅有 `HelmetIcon`, `TowerCraneIcon`, `BlueprintCornerIcon` 三个占位
  - 空状态：缺少统一工程蓝图风格空状态，不同模块空状态表现不一致
- 移动端：
  - 样式 Token：`zw-insight-app/src/styles/{tokens,signature}.css`
  - TabBar：`zw-insight-app/src/pages.json` 仅配置文字，缺高清图标
  - 水印：虽然存在 `watermarkCompositor.ts`，但在 `pages/site/watermark-camera/index.vue` 实际直接返回了原图，属于假水印提示

## 2. 真实数据链与口径基线
1. **成本五态**：
   - 接口：`GET /api/v1/dashboard/project/{projectId}/cost-control`
   - DTO：`ProjectCostControlDTO`
   - 五态字段：`baselineTotal` -> `currentTotal` -> `commitmentTotal` -> `actualTotal` -> `forecastTotal` (EAC)
   - 偏差字段：`varianceAmount = currentTotal - forecastTotal`，`varianceRate = varianceAmount / currentTotal * 100`
   - 剩余预算：`remainingBudget = currentTotal - actualTotal`（不可曲解为未承诺预算）
   - 月度趋势：服务端目前硬编码返回空列表 `trends: []`，前端必须明确展示“暂无月度历史数据能力”，绝不能生成平滑模拟曲线。
2. **生命周期总图 (V1)**：
   - 接口：`GET /api/v1/dashboard/project/{projectId}/overview`
   - 提供预算、进度、合同、产值聚合数据，目前无历史阶段时间轴事件表
   - V1 仅展示当前状态与概览，禁止伪造历史流转日期。
3. **流程与审批**：
   - 接口：`GET /api/v1/workflow/approval/detail/{taskId}` 返回 `Map<String, Object>`
   - 缺少强类型 DTO，先做白名单安全适配，在 M4 补全强类型 `ApprovalDetailDTO`。
4. **现场与水印证据**：
   - 移动端目前为本地 Canvas 合成，仅能声称“本地水印已合成”，严禁展示“防伪影像已存证”或“服务端已核验”等虚假声称。
   - 服务端目前仅有 `SignController`（定位签到），尚无 `SiteEvidence` 照片存证实体表与核验接口。

## 3. 专项门禁
- 保持所有修改有单测与覆盖率支撑
- 每次提交保持独立原子性，回滚命令清晰
- 严禁修改或暂存 `ui-evolution-demo/`
