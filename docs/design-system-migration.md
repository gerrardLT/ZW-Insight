# 设计系统迁移记录：蓝色 SaaS → Industrial Precision

> 依据：[docs/DESIGN-mix-industrial-precision.md](./DESIGN-mix-industrial-precision.md)
> 完成时间：2026-08-27
> 范围：`zw-insight-web` 前端全量样式层；零业务逻辑改动

## 变更原因

旧主题为通用 SaaS 蓝（`#3370ff`）+ 渐变 + 卡片阴影，与产品「工程项目管理平台」的工程属性脱节，且品牌蓝与 warning 橙 `#ff7d00` 在语义上互相干扰。新主题「Industrial Precision」以 Construction Safety Orange `#ff6b00` 为品牌色，混合 SpaceX（工程字体/大写宽字距）、Linear（surface 阶梯/hairline/无阴影）、NVIDIA（2px 直角/角标）三套语言，建立「橙底必配深字」「唯一浮层阴影」「直角纪律」三条承重规则。

## 四阶段影响范围与回滚

> 事实订正（2026-08-28 核验）：四阶段实际合并在**单个提交 `e5bfbf4`**（70 文件，带 `[skip ci]`）中落地，**无法按阶段单独 revert**；下表「影响文件」列仍按阶段划分供定位参考，回滚只能整体 `git revert e5bfbf4`。注意后续提交 `e84d921`（修复本提交引入的 dashboard 模板损坏）、`3854462`（详情抽屉）均触碰 `dashboard/index.vue`，revert 会产生冲突需人工解决。

| 阶段 | 影响文件 | 回滚方式 |
|---|---|---|
| Phase 1：Token 与 Element 桥接 | `src/styles/tokens/{base,light,dark}.css`、`src/styles/element-override.scss`、`src/styles/global.scss`、`src/main.ts`（字体引入）、`package.json`（+3 依赖） | 整体 revert `e5bfbf4`（见上注） |
| Phase 2：壳层与签名时刻 | `src/layouts/DefaultLayout.vue`、`src/views/login/index.vue`、`src/views/login/forgot-password.vue`、`src/views/dashboard/index.vue`、`src/components/GanttChart.vue` | 整体 revert `e5bfbf4`（见上注） |
| Phase 3：动效与图标 | `src/styles/global.scss`（旋转方块/脉冲）、`src/components/icons/zw/*`（新增）、`src/views/login/index.vue`（图标引用） | 整体 revert `e5bfbf4`（见上注） |
| Phase 4：图表色板/硬编码/基线 | `src/constants/chart-theme.ts`（新增）、`src/views/dashboard/index.vue`、`src/views/contract/index.vue`、`src/__tests__/{dashboard-index,archive-dashboard-matrix}.component.test.ts`、`e2e/visual-snapshots/*`（7 页基线重生成） | 整体 revert `e5bfbf4`（见上注） |

## Token 新旧映射表（核心）

| Token | 旧值 | 新值 | 说明 |
|---|---|---|---|
| `--zw-brand` | `#3370ff` | `#ff6b00` | 安全橙 |
| `--zw-brand-hover` | `#4e83fd` | `#ff8a2e` | |
| `--zw-brand-active` | `#245bdb` | `#e05f00` | |
| `--zw-brand-light` | `#e8f3ff` | `#fff0e3` | |
| `--zw-brand-gradient` | `linear-gradient(...)` | `#ff6b00`（纯色） | 变量名保留，8 处引用零改动去渐变 |
| `--zw-on-primary` | （无） | `#14161a` | 新增：橙底深字（白字仅 2.9:1） |
| `--zw-warning` | `#ff7d00` | `#f7b500` | 与品牌橙分离 |
| `--zw-success` | `#00b42a` | `#1f9d55` | |
| `--zw-danger` | `#f53f3f` | `#d92d20` | |
| `--zw-info` | `#86909c` | `#2b6cb0` | |
| `--zw-bg-page` | `#f7f8fa` | `#f6f7f5` | 混凝土白 |
| `--zw-bg-sidebar` | `#1e1e2d` | `#101214` | 常驻石墨黑 |
| `--zw-radius-xs/sm/md/lg` | `4/6/10/16px` | `2/4/6/8px` | 直角纪律 |
| `--zw-shadow-sm/md/lg/xl` | 四级阴影 | `none` | 变量名保留防引用报错 |
| `--zw-shadow-overlay` | （无） | `0 4px 16px rgba(20,22,26,0.12)` | 唯一合法阴影（浮层专用） |
| `--zw-sidebar-width` | `240px` | `220px` | |
| `--zw-font-display` | （无） | `'Barlow Condensed','Arial Narrow',sans-serif` | 新增 Display 层 |
| `--zw-hazard-yellow` | （无） | `#ffc400` | 新增：警示条纹 |

暗色模式按石墨阶梯 `#101214 → #23262c` 同步重写（`tokens/dark.css`）。

## 关键实现决策

1. **Token 值替换为主干**：131 个 .vue 文件绝大多数零改动；`--zw-brand-gradient` 保留变量名改纯色，使存量渐变引用即时失效。
2. **橙底深字承重规则**：`element-override.scss` 覆盖 `.el-button--primary`、checkbox/switch/radio 选中勾、warning 型组件文字为深色；主按钮去悬浮上浮改「压合」（`:active translateY(1px)`）。
3. **签名组件以工具类落地**：`.card-corner-marked`（L 角标）、`.hazard-divider`（45° 黑黄斜纹）、`.eyebrow-cap`（大写眉题）、`.status-badge-overdue`（逾期徽章，`--pulse` 修饰类开启脉冲），页面按需挂类。
4. **签名加载符**：`.el-loading-spinner` 圆形覆盖为 2px 三面灰一面橙方块（800ms/圈 linear）。
5. **字体自托管**：`@fontsource/barlow-condensed`（700）+ `@fontsource/jetbrains-mono`（400/500/700），零 CDN；D-DIN 许可未确认前用 Barlow Condensed 回退；中文零 webfont。
6. **图标全量迁移**（2026-08-27 追加）：存量 Element Plus 图标已全部替换为 Tabler——`src/components/icons/registry` 保留 88 个原名的映射层（底层换 `@tabler/icons-vue` 实现），调用方零改名；`@element-plus/icons-vue` 已卸载。工程专属符号自绘于 `src/components/icons/zw/`（塔吊/蓝图角标/安全帽，24px 网格 2px 线宽方帽），并经 `el-empty` 的 `#image` 插槽接入空态（`.zw-empty-icon` 全局类，64px/`--zw-text-quaternary`）。
7. **图表受限色板 + 暗色即时联动**：`src/constants/chart-theme.ts` 亮/暗两套；多序列默认灰阶，单一橙色关键序列，语义色与全局 token 对齐。canvas 不读 CSS 变量，故提供集中式填充器 `applyChartTheme(option, theme)`（不覆盖调用方显式字段），各持有方缓存最近一次数据并 `watch(() => appStore.isDark)` 以缓存数据重建 option 重绘——主题切换即时换色，不刷新页面、不重复请求。

## 验证结果

- `npm run build` 通过（chunk >500kB 警告为预先存在，与本次无关）
- `npm run test`：103 个测试文件 / 1102 用例全绿（含暗色重绘、逾期卡、空态自绘图标新增用例）
- 后端 `RetentionMoneyServiceTest` 9/9 通过（`mvn -pl zw-finance test`，含 `/overdue` 查询窗口边界断言）
- `npm run test:e2e:visual:update`：7 页基线重新生成；`test:e2e:visual` 复跑 8/8 通过（`maxDiffPixelRatio: 0.02` 未放宽）
- 目视核对：橙底深字按钮、侧边栏激活橙条、统计卡角标、登录页 hazard 条纹、空态塔吊/蓝图自绘图标均符合设计文档
- 事实订正（2026-08-28 核验）：`e5bfbf4` 自身携带 `dashboard/index.vue` 模板闭合损坏，推送后 CI 三连败，由 `e84d921` 修复并部署；上表验证结论以修复后状态为准

## 移动端迁移（2026-08-28 批次）

> 范围：`zw-insight-app`（uni-app，29 页面）样式层；零业务逻辑改动、零新增 Vue 组件、零 webfont（系统字体栈）；仅交付亮色主题（token 结构 dark-ready）。
> 目标：与 PC 端 token 同名（`--zw-*`）、签名组件同源、三条承重规则同守（橙底必配深字 / 唯一浮层阴影 / 直角纪律）。

### 提交粒度（每 Phase 独立提交，可单独 revert——吸取 PC 单提交 `e5bfbf4` 教训）

| 提交 | 阶段 | 内容 |
|---|---|---|
| `78428b3` | Phase 0 | 签到孤儿路由修复（`pages/mine/sign` 注册）+ `pages-registry.test.ts` 双向钉住死路由/孤儿页 |
| `9e90204` | Phase 1 | `src/styles/{tokens,signature}.css` 新增（`:root, page` 双写）、App.vue 引入、uni.scss 色板重写、pages.json tabBar 文本化（删 8 个 11 字节占位坏图）、vite H5 代理 |
| `f3dc2cc` | Phase 2 | 壳层签名时刻：登录/忘记密码（石墨黑画布 + 橙方块 ZW 铭牌 + eyebrow）、我的（石墨黑头部）、首页（L 角标统计卡 + 眉题）、工作台（status-badge）、OfflineBanner（hazard 竖条）、签到（品牌橙纯色卡） |
| `d854b2c` | Phase 3a | finance 9 页 + material 3 页 hex 清扫 |
| `6e8af83` | Phase 3b | site 5 页 + approval 2 页 + project/archive 进度条 hex 清扫 |
| `87d7573` | Phase 3c | message-center / password / shortcut-edit 清扫；shortcut-edit 去悬浮上浮阴影改压合（`:active translateY(1px)`）+ 顶部 hairline |

### 色值映射表（清扫依据，与 PC `tokens/light.css` 同名）

| 旧值 | 新值 | 语义 |
|---|---|---|
| `#409eff`（文字/边框/图标） | `var(--zw-brand)` | 品牌橙 |
| `#409eff` 填充背景且配 `#fff` | `var(--zw-brand)` + `var(--zw-on-primary)` | 橙底深字承重规则 |
| `#409eff` / `#a0cfff` 禁态 | `var(--zw-brand)` + `opacity: 0.4` | 避免橙底白字违承重规则 |
| `#66b1ff` / `#a0cfff` / `#b3d8ff` / `#ecf5ff` | `--zw-brand-hover` / `--zw-brand-light` | 品牌阶梯 |
| `#303133` / `#606266` / `#909399` / `#c0c4cc` | `--zw-text-{primary,secondary,tertiary,quaternary}` | 文字四级 |
| `#dcdfe6` / `#e4e7ed` / `#ebeef5` | `--zw-border` / `--zw-border-light` | hairline 双级 |
| `#f5f5f5` / `#f0f0f0` / `#f5f7fa` | `--zw-bg-page` / `--zw-bg-hover` | 画布与分区底 |
| `#f56c6c` / `#fef0f0` / `#fde2e2` | `--zw-danger` / `--zw-danger-light` | 语义红 |
| `#67c23a` / `#f0f9eb` | `--zw-success` / `--zw-success-light` | 语义绿 |
| `#e6a23c` / `#fdf6ec` / `#faecd8` / `#ffd666` | `--zw-warning` / `--zw-warning-light` | 语义黄（与品牌橙分离） |
| `135deg 紫渐变 #667eea→#764ba2` | `--zw-bg-sidebar` 纯色 | 石墨黑控制室 |
| `135deg 蓝渐变 #409eff→#66b1ff` | `--zw-brand` 纯色 | 去渐变纪律 |
| `border-radius 10/12/16rpx`（按钮 `44rpx` 药丸） | 上限 `--zw-radius-lg`（8px）/ 按钮 `--zw-radius-sm`（4px） | 直角纪律 |

### 签名工具类（从 PC `global.scss` 移植，rpx 适配）

`.zw-btn-primary`（44px 高、直角、橙底深字、压合反馈）、`.card-corner-marked`（L 角标）、`.hazard-divider`（45° 黑黄斜纹）、`.eyebrow-cap`（大写宽字距眉题）、`.status-badge-{success,warning,danger,info}`（soft 底 + 深字）、`.zw-card`（白底 + hairline + 直角）。

### 验证结果

- `npx vitest run`：17 个测试文件 / 127 用例全绿（含 Phase 0 新增 `pages-registry.test.ts`）
- 行覆盖率 874.5‰（基线 875‰，样式层改动对覆盖率中性，千分位精度内持平）
- `npm run build:h5` 通过
- grep 审计：26 个旧色值（含渐变）全库零残留；字面量圆角仅剩 2/4rpx（在上限内）
- 目视核对：登录页截图归档 `audit-reports/login-mobile-viewport.png`——石墨黑画布、橙方块 ZW 铭牌、橙底深字按钮三项均确认；其余 4 关键页（首页/工作台/审批/我的）需登录态，预览服务核验（实事求是标注）

### 暗色批次（2026-08-28 追加，遗留项 1 关闭）

**机制**：纯 CSS 跟随系统——[tokens.css](../zw-insight-app/src/styles/tokens.css) 末尾 `@media (prefers-color-scheme: dark)` 覆盖块，选择器 `:root, page` 双写；零 JS、零持久化，H5 与 mp-weixin 均原生支持该 media query。色值镜像 PC `tokens/dark.css` 石墨阶梯（`#101214 → #23262c`）。

| 提交 | 内容 |
|---|---|
| `1b54594` | 暗色 token 覆盖块 + 新增 `--zw-bg-mask` 替换 14 处硬编码遮罩；语义色实心底白字 ×5（红/绿）改 `--zw-text-inverse`（暗色提亮后自动翻深，承重规则同源）；checkbox「白勾藏底」改 transparent（暗色下会显形） |
| `885f97a` | `tests/tokens-dark.test.ts` 契约测试（media 块存在 / 双写选择器 / 石墨阶梯关键值 / 遮罩零残留） |
| `b3cb00c` | H5 兜底：原生导航栏/tabBar 内联静态色不读 token，media 内 `uni-page-head`/`uni-tabbar` 元素选择器 `!important` 覆盖（仅 H5 存在，mp-weixin 天然失效） |

**验证**：18 测试文件 / 134 用例全绿；行覆盖率 874.5‰ 持平；`build:h5` 通过；Playwright `colorScheme: 'dark'` 真实 scheme 截图归档 `audit-reports/{login,home}-dark-mode.png`——页面背景 `#101214`、卡片 `#16181c`、橙底深字按钮、导航栏/tabBar 暗底均确认。

**新遗留**：`pages.json` `globalStyle.backgroundColor` 与 mp-weixin 原生导航栏/tabBar 为静态值，暗色下小程序端窗体底色仍亮（H5 已兜底）；随 mp-weixin 真机核验遗留项一并处理。

### 遗留项（移动端）

1. ~~**移动端暗色模式未建**~~ → **已落地**（2026-08-28 暗色批次，见上）。
2. **间距层级未全量清扫**：页边距/卡间现状已接近 16/12，收益低风险高，保持现状。
3. **mp-weixin 真机 CSS 变量核验**：tokens 选择器已 `:root, page` 双写，本批以 H5 为验证基准，小程序真机渲染（含原生导航栏/tabBar 静态色）待核验。
4. **首页/工作台/审批/我的目视核对**：依赖登录态与真实后端数据，随部署后人工补齐截图。

## 遗留项

1. **监控大屏模式未建**：设计文档 Large Display 章节当前无页面载体。
2. ~~**移动端壳层未建**~~ → **主题已落地**（2026-08-28）：`zw-insight-app` 已完成 Industrial Precision 迁移（见上文「移动端迁移」章节），遗留移动端暗色模式与 mp-weixin 真机核验。
3. **D-DIN 许可**：确认后可将 `--zw-font-display` 首项替换为 D-DIN，回退链不变。

## 已完成的原遗留项（2026-08-27 追加批次）

1. **存量图标全量迁移**：Element Plus → Tabler（映射注册表 + 卸载旧依赖），原遗留项 1 关闭。
2. **图表暗色即时联动**：`applyChartTheme` + 缓存重绘，主题切换不刷新页面，原遗留项 4 关闭。
3. **逾期徽章载体落地**：`.status-badge-overdue--pulse` 首接工作台「质保金逾期风险」卡（真实端点 `/overdue`，与催办任务同口径：ACTIVE 且 expireDate < 当日）。
4. **空状态接自绘图标**：`StatChartPanel`（空态蓝图角标/失败态安全帽）与 `project-dashboard`（引导塔吊 + 四维度蓝图角标）经 `#image` 插槽接入。

## 已完成的原遗留项（2026-08-28 追加批次）

1. **`/overdue` 端点部署 + 基线刷新**：端点随 `e5bfbf4` 后端改动经 2026-08-28 部署上线（裸探活 401 非 404，鉴权生效）；同日重跑 `test:e2e:visual:update`，`dashboard.png` 重生成（逾期卡由错误态转为真实数据态，67KB→52KB），比对复跑 8/8 通过（`maxDiffPixelRatio: 0.02` 未放宽）。原遗留项 4 关闭。
2. **文档事实订正**：回滚粒度（四阶段实为单提交 `e5bfbf4`，无法分阶段 revert）与 CI 三连败事实（`e84d921` 修复）已在上文标注。

---

## 前端深度优化 · 首轮（Batch 0 契约层 + Batch 1 插图/空态，2026-09-17）

> 范围：`zw-insight-web` + `zw-insight-app` 样式层/组件层；零业务逻辑、零接口改动。
> 依据：批准计划《前端深度优化分批实施》+ `docs/DESIGN-mix-industrial-precision.md`（承重墙）+ `docs/DEEP_RESEARCH_前沿设计与交互_2026.md`（前沿基线）。
> 设计基调裁定：**坚守并深化「工业精密」纪律**（零阴影/零渐变/2-8px 直角/单橙 #ff6b00）——调研与双端审计一致判定该语言属赛道头部，不推倒。

### 0.1 前沿校准结论（关键纠偏，实事求是）

计划 Batch 0.4 原措辞「palette 重排为色盲安全序（Okabe-Ito 派生）」经代码/文档实证**与 DESIGN 承重墙冲突**，据「Code/配置 > 上下文结论」优先级**修正实现**：
- DESIGN L719-724/L849 明定：多序列 = **中性灰阶**（固定 `#8a8f98→#b9bdb6→#d9dcd6`）、**禁止第三品牌色做序列区分**、色盲安全靠**线型（实线/虚线）+ 文字标签双通道**。
- 故 0.4 **不 rainbow 化 palette**（那会摧毁工业精密识别度，正是计划 Rejected Alt #1 所拒），改为**新增 `lineStyles` 线型第二通道**（DESIGN 明处方）+ 域色双源守护测试。
- 前沿基线（`DEEP_RESEARCH_前沿设计与交互_2026.md`，2 天前，三方交叉验证 Atlassian Motion/Cloudflare/Datawrapper）已充分，按 rule #11 不做冗余 web 重调研。
- 动效纪律纠偏：`base.css` L100「禁用弹跳反模式」为明确代码决策，故 `--zw-ease-out-back`/`-spring` **保持无 overshoot**（DESIGN L618 的 toast 微回弹例外刻意不启用），仅修正误导性注释。此即计划 Rejected Alt #4。

### Batch 0 · 契约层（零风险，不改页面视觉）

| 项 | 影响文件 | 内容 | 回滚 |
|---|---|---|---|
| 0.2 动效 token 语义分层 | `zw-insight-web/src/styles/tokens/base.css`、`zw-insight-app/src/styles/tokens.css` | 新增退场时长 token（exit-fast/dropdown/modal/drawer = 入场×70%，DESIGN「出场快于入场」）；移动补 instant/exit-fast/ease-in；修正 ease-out-back/-spring「无 overshoot」误导注释 | git revert 本次提交 |
| 0.3 微动效原语层 | `zw-insight-web/src/styles/global.scss`、`zw-insight-app/src/styles/signature.css` | 新增 utility：`zw-anim-{list-enter,state-swap,press-fit,optimistic}`（双端）+ `zw-count-up`（PC 样式契约）+ `zw-anim-pull-refresh`（移动）；**仅 transform/opacity（GPU 合成）**，均被既有 `prefers-reduced-motion` 块降级；不自动挂载（Batch 2 按需消费） | 同上 |
| 0.4 图表色板 SSOT | `zw-insight-web/src/constants/chart-theme.ts` | 新增 `lineStyles`（色盲安全线型第二通道）+ `chartSeriesLineStyle()` helper；**palette 守灰阶纪律不变**、`highlight` 恒 #ff6b00、`applyChartTheme` 契约不变（既有断言全绿） | 同上 |
| 0.5 域色双源守护 | `zw-insight-web/src/__tests__/domain-visual-tokens.test.ts`（新增） | 解析 `base.css --zw-domain-*` 与 `business-visual.ts` 逐域比对，漂移即 FAIL；含孤儿 CSS 变量 + borderColor==color 校验 | 删除测试文件 |
| 0.6 插图盘点 + 契约 | 见下表 | 4 态 × 双主题 × 双端插图矩阵契约化 | — |

### 0.6 插图资产盘点与契约矩阵

**现有资产**：PC `assets/`（empty-blueprint{,-dark}.png、err-403{,-dark}.png、err-404{,-dark}.png、login-bg-site.png、default-avatar.png、logo{,-light}.png）+ SVG 组件（visual/ZwStateIllustration【新】、ZwBlueprintEmpty、ZwCostFiveState、ZwCredentialPanel、ZwLifecycleRail、ZwScheduleLegend；icons/zw/*）；移动 `static/`（brand/empty-blueprint{,-dark}.png、bizicons/*.png ×8、tabbar/*.png ×8）+ ZwiEmptyState data-URI SVG【新】。

**4 态 × 双主题 × 双端契约矩阵**（本轮建立）：

| 态 | PC（ZwStateIllustration 内联 SVG，CSS 变量自动双主题） | 移动 H5（ZwiEmptyState data-URI SVG，JS 烘焙亮/暗双层） | 移动 mp-weixin |
|---|---|---|---|
| data | 空账本行 + 橙游标点 | 同 PC 图形，中性 #a3a8b0/#5a5f66 + 橙 #ff6b00 | empty-blueprint{,-dark}.png（单图，遗留） |
| error | 警示三角 + 橙感叹号 | 同 | 同上（遗留：待 4 态 PNG） |
| offline | 断连云 + 橙斜杠 | 同 | 同上（遗留） |
| permission | 挂锁 + 橙锁孔 | 同 | 同上（遗留） |

纪律：线条恒中性 `--zw-text-quaternary`、单一 `--zw-brand` 橙点缀；**形状区分状态**（非颜色，色盲安全）；SVG 颜色经 **CSS 类注入**（presentation attribute 不支持 `var()`，已联网核实 + 代码库既有硬编码 hex 佐证）。

### Batch 1 · 插图与空态系统化（低风险，高感知）

| 项 | 影响文件 | 内容 | 回滚 |
|---|---|---|---|
| 1.1 PC 空态统一 | `components/ZwEmptyState.vue`、`components/visual/ZwBlueprintEmpty.vue`、`__tests__/ZwEmptyState.component.test.ts` | ZwEmptyState 由 EP 通用图标升级为品牌化蓝图 SVG 4 态（经 ZwStateIllustration）+ 可选 title + 方向性默认文案 + #image slot（bespoke 覆盖）；ZwBlueprintEmpty 去 8 处硬编码 `#ff6b00`→CSS 类；测试同步升级 | git revert |
| 1.2 插图系统化 | `components/visual/ZwStateIllustration.vue`（新增）、`__tests__/ZwStateIllustration.component.test.ts`（新增） | 抽 4 态蓝图为可复用具名组件（size/type/ariaLabel props），ZwEmptyState 消费；单一事实源，可供错误页/帮助中心/全局错误边界复用 | 删除组件 + 还原 ZwEmptyState 内联 SVG |
| 1.3 移动 4 态拆分 | `zw-insight-app/src/components/zwi/ZwiEmptyState.vue`、`tests/components/zwi-shell.test.ts` | 4 态由共用 1 张 PNG 拆为专属 data-URI SVG（形状区分）；**条件编译**：H5 走 SVG、mp-weixin 回落既有 PNG（uni 官方：小程序 `<image>` 仅支持网络 SVG，已核实）；测试补「4 态 SVG 互不相同」断言 | git revert |
| 1.4 StatChartPanel 接入 | `components/StatChartPanel.vue` | 空/错态由裸 `el-empty`+`HelmetIcon`/`<img>` 混用改为统一 ZwEmptyState（error→默认 SVG，data→经 #image slot 保留 bespoke AI PNG 并补 alt 修无障碍）；既有三态契约断言全绿 | git revert |

### 计划偏差与遗留（实事求是登记）

- **偏差 1（0.4）**：palette 未按计划措辞 rainbow 重排，改为 lineStyles 线型通道——依据 DESIGN 承重墙，见 0.1。
- **偏差 2（1.2）**：插图从「ZwEmptyState 内联」提升为「独立可复用组件 ZwStateIllustration」——单一事实源 + 可复用，优于内联锁死。
- **偏差 3（1.3）**：mp-weixin 未获 4 态专属插图（`<image>` 不支持 data-URI SVG），回落单张 PNG，**零回归**。
- **遗留 1**：mp-weixin 4 态专属插图——待生成 4×2 品牌 PNG（走项目统一生图 API + 视觉快照评审）或真机核验 SVG 网络地址方案。
- **遗留 2**：PC 无 500 错误页（仅 403/404，均已有主题化 AI PNG）；如补 500 页属路由/视图新增，非纯插图，留待 Batch 4 组件批次评估。
- **遗留 3**：PC 22 处原生 `el-empty` 未强制迁移到 ZwEmptyState（避免大范围回归），采增量采用；1.4 已树立首个可见样板。

### 验证结果（Batch 0 + 1）

- PC `npm run test`：全量 vitest（含新增 domain-visual-tokens / ZwStateIllustration + 升级 ZwEmptyState）——见提交时终端汇总。
- 移动 `npx vitest run`：zwi-shell 等全绿（ZwiEmptyState 22 项含新增 4 态插图断言）+ `npm run build:h5`。
- 无接口改动，一致性审计不触发；无硬编码 hex 新增（新 SVG 一律 CSS 类注入，ZwStateIllustration 测试断言 `not.toMatch(/#[0-9a-fA-F]{6}/)`）。

---

## 前端深度优化 · 第二轮（Batch 2 动效与微交互，2026-09-17）

> 范围：把 Batch 0.3 微动效原语应用到真实交互；遵 DESIGN Motion 承重墙（仅用户触发、单焦点、出场=入场×70%、禁页面挂载入场动画、账本滚动为唯一例外）。

### 已交付（均已被真实消费，非死代码）

| 项 | 影响文件 | 内容 | 回滚 |
|---|---|---|---|
| 2-A1 徽章 state-swap | `zw-insight-web/src/components/ZwStatusBadge.vue` | 补 `transition: background-color/color 150ms ease-out`（DESIGN L627「badge 切换」）；同元素 type 变更时底色/文字平滑过渡 | git revert |
| 2-A3 命令面板退场节奏 | `zw-insight-web/src/components/CommandPalette.vue` | 退场分离为 `--zw-duration-exit-fast/-exit-dropdown`（入场×70%）+ `ease-in` 加速离开（DESIGN L595/L620「出场快于入场、退场 ease-in」）；reduced-motion 块已覆盖 | git revert |
| 2-B2 移动端触觉反馈 | `zw-insight-app/src/utils/haptic.ts`（新）、`components/zwi/ZwiFormPage.vue`、`tests/haptic.test.ts`（新） | `hapticTap()` 封装 `uni.vibrateShort`（typeof 守护 + try/catch 静默降级，无硬件/测试环境不报错）+ 全局开关；接入 ZwiFormPage 提交路径（**一处覆盖 21 个表单页**），现场手套/强光下用触觉确认「已触发」 | git revert / 删除 util |

### 本轮刻意延后项（实事求是登记，非静默跳过）

- **2-A2 KPI 账本滚动（count-up）**：dashboard `statCards` 值为 `formatWan()` 字符串（非数值），且 `dashboard-index.component.test.ts`（19 测试）用真实 timers + flushPromises 断言最终格式化字符串；接入 count-up 需数值重构 + 新增子组件 + 测试改 fake-timer/rAF 推进。在广泛批次里动摇关键组件违背视角 C（最小风险），且单独建 composable 会成未接线死代码——**整体延后为专项子批**（composable + 接线 + 测试一起做）。`--zw-duration-count`(800ms) token 已就绪。
- **表格行进场 / 乐观提交勾选**：DESIGN L594 禁页面挂载入场动画，行进场仅限用户触发的增/删（L635），需逐页分析插入点；乐观提交需可复用按钮封装——均归 Batch 4 组件打磨批次。
- **移动端下拉刷新指示器 / 列表进场 / 离线队列角标动效 / tabBar 切换**：下拉刷新需统一 scroll-view refresher 机制（多页）；离线队列角标依赖 Batch 4.5 离线透明化先落地；tabBar 为原生控件 CSS 可控面有限。均归后续专项。
- **`.active-press` 统一铺开**：核发现 ZwiCell 等已用 uni 原生 `hover-class`（跨端比 `:active` 更可靠），`.active-press`（scale+opacity）为另一种风味，强推会与 hover-class 重叠——尊重既有范式，不强行铺开。

### 验证结果（Batch 2）

- 移动 `npm test`：**30 文件 / 246 通过**（含新 haptic 4 项 + 21 个用 ZwiFormPage 的页面测试全绿）；`npm run build:h5` Build complete。
- PC `npm run test`：全量绿（A1/A3 为纯 CSS transition 变更，无 DOM/逻辑改动，零测试影响；command-palette 9 项单独复验通过）。
- 无接口改动；触觉反馈失败为真实静默降级（无硬件时 no-op），非假成功。

---

## 前端深度优化 · 第三轮（Batch 3 配色与主题层次深化，2026-09-17）

> 范围：颜色 SSOT 清理 + 对比度 AA + 色盲安全线型落地 + 移动端焦点环；遵「令牌三主题同步」纪律。

| 项 | 影响文件 | 内容 | 回滚 |
|---|---|---|---|
| 3.1 游离硬编码回落 token | `zw-insight-web/src/styles/element-override.scss` | 分页激活文字 `#f2f3f1` → `var(--zw-steel-text)`（同值，零视觉变化，固定深底浅字用 steel-text 而非随主题变的 --zw-text） | git revert |
| 3.2 图标 SVG 去硬编码 | `icons/zw/{CostFiveStateIcon,WbsIcon,EvidenceIcon}.vue`、`styles/global.scss` | 3 图标单一橙强调元素 `stroke="#ff6b00"` → `.zw-icon-accent` 类（global.scss 新增全局工具类，颜色经 CSS 注入）；PC 无 outdoor 主题→--zw-brand 恒 #ff6b00，零视觉变化，仅 SSOT 清理 | git revert |
| 3.3 色盲安全线型落地 | `zw-insight-web/src/views/hr/statistics.vue` | 月度趋势入职/离职双序列折线（绿/红——红绿色盲痛点）加 `lineStyle.type = chartSeriesLineStyle(i)`（入职实线/离职虚线），启用 Batch 0.4 线型第二通道（DESIGN L849）；仅加 type 不动颜色 | git revert |
| 3.4 移动端亮面色阶对齐 AA | `zw-insight-app/src/styles/tokens.css`、`components/zwi/ZwiEmptyState.vue` | light `--zw-text-tertiary` #7c828c→#686d75（5.36:1）、`--zw-text-quaternary` #a3a8b0→#6b727d（4.52:1），镜像 PC light.css（三主题同步 + 修 placeholder 对比度 3.23:1 FAIL）；同步 ZwiEmptyState `NEUTRAL_LIGHT`→#6b727d（双源镜像） | git revert |
| 3.6 移动端焦点环 | `zw-insight-app/src/styles/tokens.css` | 补全局 `:focus-visible`（2px 品牌橙 + 2px gap，与 PC global.scss 同源）；`#ifdef H5` 限定（mp-weixin wxss 对 :focus-visible 支持有限，不注入） | git revert |

### 3.5 户外高对比模式（实事求是核发现）

- **开关已存在**：`pages/mine/index.vue` L38-44 已有「现场强光高对比模式」 switch + L60-89 持久化（`uni.setStorageSync('zw_outdoor_mode')`）+ `applyOutdoorTheme()`；critique（2026-09-11）「未推广/无入口」已不成立。
- **真实缺口（延后专项）**：① `applyOutdoorTheme` 仅用 `document.documentElement/body`（**仅 H5 生效**），mp-weixin 无 `document` → tokens.css 的 `page.theme-outdoor`/`.theme-outdoor` 选择器从未被加类，outdoor 在小程序端实际未应用（需页根 class 绑定 + 全局 store，侵入式且 mp 真机未核验）；② 首次强光环境提示未做。二者属跨端架构/UX 功能级，不在配色批次做半截实现。

### 验证结果（Batch 3）

- PC `npm run test`：**114 文件 / 1197 通过 + 2 跳过**（hr-statistics lineStyle 新增不破断言——测试仅钉 series name/data/length）。
- 移动 `npm test`：**30 文件 / 246 通过**；`npm run build:h5` Build complete（`#ifdef H5` focus-visible 编译正常）。
- 无接口改动；无新增硬编码 hex（反而清 4 处：element-override 1 + icons 3）。
