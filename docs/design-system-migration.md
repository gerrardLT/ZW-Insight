# 设计系统迁移记录：蓝色 SaaS → Industrial Precision

> 依据：[docs/DESIGN-mix-industrial-precision.md](./DESIGN-mix-industrial-precision.md)
> 完成时间：2026-08-27
> 范围：`zw-insight-web` 前端全量样式层；零业务逻辑改动

## 变更原因

旧主题为通用 SaaS 蓝（`#3370ff`）+ 渐变 + 卡片阴影，与产品「工程项目管理平台」的工程属性脱节，且品牌蓝与 warning 橙 `#ff7d00` 在语义上互相干扰。新主题「Industrial Precision」以 Construction Safety Orange `#ff6b00` 为品牌色，混合 SpaceX（工程字体/大写宽字距）、Linear（surface 阶梯/hairline/无阴影）、NVIDIA（2px 直角/角标）三套语言，建立「橙底必配深字」「唯一浮层阴影」「直角纪律」三条承重规则。

## 四阶段影响范围与回滚

每阶段为独立提交粒度，可单独 `git revert`。

| 阶段 | 影响文件 | 回滚方式 |
|---|---|---|
| Phase 1：Token 与 Element 桥接 | `src/styles/tokens/{base,light,dark}.css`、`src/styles/element-override.scss`、`src/styles/global.scss`、`src/main.ts`（字体引入）、`package.json`（+3 依赖） | `git revert <P1-commit>` |
| Phase 2：壳层与签名时刻 | `src/layouts/DefaultLayout.vue`、`src/views/login/index.vue`、`src/views/login/forgot-password.vue`、`src/views/dashboard/index.vue`、`src/components/GanttChart.vue` | `git revert <P2-commit>` |
| Phase 3：动效与图标 | `src/styles/global.scss`（旋转方块/脉冲）、`src/components/icons/zw/*`（新增）、`src/views/login/index.vue`（图标引用） | `git revert <P3-commit>` |
| Phase 4：图表色板/硬编码/基线 | `src/constants/chart-theme.ts`（新增）、`src/views/dashboard/index.vue`、`src/views/contract/index.vue`、`src/__tests__/{dashboard-index,archive-dashboard-matrix}.component.test.ts`、`e2e/visual-snapshots/*`（7 页基线重生成） | `git revert <P4-commit>` |

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

## 遗留项

1. **监控大屏模式未建**：设计文档 Large Display 章节当前无页面载体。
2. **移动端壳层未建**：设计文档 Mobile Shell 章节当前无页面载体。
3. **D-DIN 许可**：确认后可将 `--zw-font-display` 首项替换为 D-DIN，回退链不变。
4. **后端 `/overdue` 端点待部署**：`GET /api/v1/finance/retention/overdue` 代码与单测已就绪但尚未提交/CI 部署，远程联调服务器仍 404；当前 `dashboard.png` 基线如实包含该卡的错误态（显式提示+重试，不静默）。**部署后须重跑 `test:e2e:visual:update` 刷新基线**。

## 已完成的原遗留项（2026-08-27 追加批次）

1. **存量图标全量迁移**：Element Plus → Tabler（映射注册表 + 卸载旧依赖），原遗留项 1 关闭。
2. **图表暗色即时联动**：`applyChartTheme` + 缓存重绘，主题切换不刷新页面，原遗留项 4 关闭。
3. **逾期徽章载体落地**：`.status-badge-overdue--pulse` 首接工作台「质保金逾期风险」卡（真实端点 `/overdue`，与催办任务同口径：ACTIVE 且 expireDate < 当日）。
4. **空状态接自绘图标**：`StatChartPanel`（空态蓝图角标/失败态安全帽）与 `project-dashboard`（引导塔吊 + 四维度蓝图角标）经 `#image` 插槽接入。
