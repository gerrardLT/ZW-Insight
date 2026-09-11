# PC与移动端工业精密体验升级实施计划 (Industrial Precision Advanced Features)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全量实现针对 PC 端与移动端提出的工业精密高级交互能力：PC 端表格三档密度切换与底端吸底合计条、长表单右侧工业蓝图锚点骨架导航、图表 Blueprint 网格扫描骨架与预警带；移动端强光高对比度现场模式、离线硬件指示灯与排队管理器、大拇指下沉 BottomSheet 选择器、现场工程水印相机。

**Architecture:** 双端并行增强。PC 端基于 Vue 3 + Pinia + Element Plus 深度定制，扩充 `useAppStore` 表格密度状态、Element Plus 紧凑/宽松主题类、蓝图锚点骨架与图表骨架；移动端基于 Uni-app Vue 3，扩充户外强光 Tokens、硬件质感离线指示灯微组件、大拇指下沉抽屉与 Canvas 水印相机，确保 100% 遵守直角纪律与无 Mock 数据原则。

**Tech Stack:** Vue 3, TypeScript, Pinia, Element Plus, SCSS, ECharts (PC), Uni-app Vue 3, CSS Variables, Canvas API (Mobile), Vitest (Happy-DOM).

---

### Task 1: PC 端表格密度系统与吸底合计条 (Table Density & Sticky Summary)
- [ ] 在 `zw-insight-web/src/stores/app.ts` 增加 `tableDensity: 'compact' | 'default' | 'loose'` 及切换方法并持久化
- [ ] 在 `zw-insight-web/src/styles/element-override.scss` 增加 `[data-table-density="compact"]` 与 `[data-table-density="loose"]` 样式及 `.el-table__footer-wrapper` 吸底固化
- [ ] 封装 `zw-insight-web/src/components/ZwTableDensitySwitch.vue` 微组件并在顶部/表格工具栏测试
- [ ] 编写单测验证表格密度切换与 store 响应

### Task 2: PC 端长表单右侧工业蓝图锚点骨架导航 (ZwFormAffixNav)
- [ ] 创建 `zw-insight-web/src/components/ZwFormAffixNav.vue`
- [ ] 实现标尺线、等宽编号（01/02）、平滑滚动、激活项高亮、必填校验指示灯
- [ ] 在 `zw-insight-web/src/views/contract/form.vue` 或长表单示例中集成验证
- [ ] 编写组件单元测试

### Task 3: PC 端图表 Blueprint 网格扫描骨架屏与预警带 (StatChartPanel 升级)
- [ ] 修改 `zw-insight-web/src/components/StatChartPanel.vue`：加入工业蓝图网格坐标轴、微点阵背景与激光扫描线
- [ ] 增加 `warningThreshold` 阈值线警示属性，支持超限高亮与斜角条纹背景
- [ ] 编写组件单元测试

### Task 4: 移动端强光现场模式 (Outdoor High-Contrast Mode)
- [ ] 在 `zw-insight-app/src/styles/tokens.css` 中增加 `[data-theme="outdoor"]` / `page.theme-outdoor` 变量覆写（纯黑纯白、强对比度边框、反白高饱和度铭牌）
- [ ] 在 `zw-insight-app/src/pages/mine/index.vue` 增加“现场强光模式”开关并持久化到本地存储
- [ ] 在 `zw-insight-app/src/App.vue` 启动时同步读取并挂载模式

### Task 5: 移动端离线硬件指示灯与排队微面板 (ZwOfflineIndicator)
- [ ] 在 `zw-insight-app/src/stores/network.ts` 扩展队列数量与同步状态监听
- [ ] 封装 `zw-insight-app/src/components/ZwOfflineIndicator.vue`，实现 NORMAL（绿灯微呼）、OFFLINE（琥珀黄灯+计数）、SYNCING（黄色扫光跑马灯）三种工业质感态
- [ ] 展开面板查看待同步条目与手动触发 `syncEngine.syncAll()`
- [ ] 编写单元测试

### Task 6: 移动端大拇指触控底板 (ZwBottomSheetPicker) 与工程水印相机 (ZwWatermarkCamera)
- [ ] 封装 `zw-insight-app/src/components/ZwBottomSheetPicker.vue`：48px 触控靶心、直角抽屉、震动反馈
- [ ] 封装 `zw-insight-app/src/components/ZwWatermarkCamera.vue`：工业十字瞄准线、经纬度、雪花单号、时间水印 Canvas 合成
- [ ] 编写微组件单测并执行全量测试套件回归
