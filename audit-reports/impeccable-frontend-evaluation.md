# ZW-Insight 前端全域设计评估与优化报告 (Impeccable Evaluation & Audit)

> 依据：`impeccable` 设计审查准则、Nielsen 10项可用性启发式、7维视觉DNA规范及机械检测器（`detect.mjs`）
> 日期：2026-09-09
> 范围：`zw-insight-web` (PC端管理中后台)、`zw-insight-app` (移动端现场巡检与审批)

---

## 1. 评估总览 (Executive Summary)

项目当前已从通用 SaaS 蓝转向了 **Industrial Precision（工程精密）** 体系，拥有坚固的底层色彩阶梯（混凝土白 + 石墨黑控制室 + 施工安全橙）、直角与微倒角规范以及等宽数字排版，整体专业质感显著领先同类中后台。

经 `impeccable` 机械检测器与双端界面走查，发现了以下核心优化空间：

1. **AI 界面特征性反模式：Side-Tab Accent Border**
   - 在 `dashboard/index.vue`、`dashboard/project-cost-control.vue`、`budget/cost-account/index.vue` 存在 `border-left: 3px solid var(--zw-brand/danger/warning)` 的粗单侧色条。这是典型的模板化/AI味陈旧特征。应重构为符合工业咬合的轻质角标刻度或内嵌状态标签条。
2. **布局属性动画卡顿隐患 (Layout Property Animation)**
   - `DefaultLayout.vue` 侧边栏折叠时直接对 `width` 进行动画过渡（`transition: width var(--zw-transition-slow)`），引起重排（Reflow）与布局抖动。应使用更加平滑的硬件加速配合宽度预计算，或优化其配合过渡。
3. **移动端触控目标与无障碍细节**
   - `zw-insight-app` 的快捷入口图标卡片、消息列表行高与返回按键触控热区可以更符合移动工程作业人员防误触的标准（≥44px 热区）。

---

## 2. Nielsen 10 项可用性启发式评分 (Heuristics Score)

| 编号 | 启发式原则 | 得分 (0-4) | 现状分析与改进建议 |
|---|---|---|---|
| **H1** | 状态可见性 (System Status) | **3.8** | 看板具有实时逾期脉冲徽章、CBS 进度预警、Loading 状态覆盖，反馈及时透明。 |
| **H2** | 贴合现实习惯 (Match Real World) | **3.9** | 契合工程基建术语（CBS、垫资、产值、标段、台账），安全橙与安全帽等工程实体隐喻准确。 |
| **H3** | 用户控制与自由 (User Control) | **3.7** | 支持面包屑跳转、抽屉一键退出，快捷功能个性化配置保存与重置。 |
| **H4** | 一致性与标准 (Consistency) | **3.8** | PC与移动端双端 Token 键名同名，三条承重规则（橙底深字、直角纪律、零扩散阴影）严格保持。 |
| **H5** | 防错机制 (Error Prevention) | **3.9** | 预算超支硬阻断、日期区间约束、批量审批空选守卫已全部在组件中通过断言钉住。 |
| **H6** | 识别优于回忆 (Recognition) | **3.6** | 等宽数字与千分位排版大幅减轻核算认知负荷，快捷功能提供图标化辨识。 |
| **H7** | 灵活性与效率 (Flexibility) | **3.7** | 具备全键盘快捷操作、快捷入口自定义配置、高密/正常模式适配。 |
| **H8** | 审美与克制 (Aesthetic & Minimal) | **3.5** | 大面积灰阶搭配单点安全橙。本次需消除 3 处 Side-tab 单侧粗色条与优化侧边栏过度动画。 |
| **H9** | 容错与恢复 (Help & Error Recovery) | **3.8** | 接口报错时提供卡内错误态与显式重试入口，拒绝假数据静默吞错。 |
| **H10**| 帮助与说明 (Help & Docs) | **3.5** | 字段提示（Tooltip）与指标口径定义清晰，已补充操作说明。 |

**综合设计健康度得分**：**37.2 / 40 (93.0%) - 工业级优秀**

---

## 3. 待优化项清单与落地规划

1. **PC端欢迎横幅重构**：`zw-insight-web/src/views/dashboard/index.vue`
   - 去除 `border-left: 3px solid var(--zw-brand)`，改用纯粹的石墨黑机身 + 顶沿 1px 细微高光发丝线 + 内部精致角标。
2. **PC端成本控制与成本科目卡片优化**：
   - `zw-insight-web/src/views/dashboard/project-cost-control.vue`：将 `border-left: 3px solid` 优化为左侧紧凑内嵌的 2px 状态状态圆点/徽标或整体柔和边框。
   - `zw-insight-web/src/views/budget/cost-account/index.vue`：去除 `border-left: 3px solid var(--zw-brand)`，改用统一的工程容器微阶梯边框。
3. **PC端侧边栏折叠过渡优化**：
   - `zw-insight-web/src/layouts/DefaultLayout.vue`：优化过渡曲线为 `will-change: width;` 与硬件层合成保护，减少主线程布局重排。
4. **移动端触控与排版微调**：
   - `zw-insight-app/src/pages/home/index.vue`：快捷入口增加 `:active` 微压合触控反馈，保证手指触碰时的物理跟手感。
