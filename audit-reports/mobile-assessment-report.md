# 移动端（zw-insight-app）全方位评估报告 —— 修复后终版

**评估日期**: 2026-08-29
**评估对象**: `zw-insight-app`（uni-app 3.0 + Vue3 + Pinia，36 页面 / 58 API 调用 / 18 测试文件）
**评估方式**: 四维取证（一致性审计 + 测试成熟度 + 构建可行性 + H5 真实环境走查）→ 高优修复 → 闭环复验
**执行原则**: 真实接口、不静默、不降级、不伪造

---

## 一、四维评分总览

| 维度 | 评分 | 修复前 | 修复后 | 证据 |
| --- | --- | --- | --- | --- |
| 前后端一致性 | A（95） | 0 Critical / 0 Major / 一致率 81.18% | 持平：0 Critical / 0 Major / 一致率 81.18%（后端 742 / PC 629 / 移动端 58） | `audit-report-2026-08-29T21-43-15.md` |
| 测试成熟度 | A-（88） | 18 文件 131 用例全绿，覆盖率 875‰ | 18 文件 **141 用例**全绿，覆盖率 **879‰**（基线同步上调，只升不降） | `tests/frontend-coverage-baseline.json`、`npm run test:coverage` |
| 构建与发布就绪 | B+（82） | `build:h5` 可过但 CI 无门禁；manifest 权限缺失；BASE_URL 写死 | `build:h5` 通过并纳入 CI 门禁；manifest 权限补齐；BASE_URL 收敛至 `utils/env.ts` | `.github/workflows/deploy.yml`、`src/manifest.json` |
| 真实运行表现（H5 走查） | B+（84） | 4 项 P0 数据完整性/需求闭环缺陷 + 3 项走查实证缺陷 | 全部修复并走查复验通过（离线入队→联网重放→服务端落库全链路实证） | `mobile-screenshots/walk-01~09*.png` |

**综合结论**: 接口层干净（审计连续两轮 0 Critical），主要风险集中在离线写入链路与多端发布配置。本轮修复后，移动端达到「可发布 H5、小程序/App 发布前置项已登记」状态。

---

## 二、问题清单（编号 + 严重级 + 处置）

### P0（数据完整性 / 需求闭环）—— 已全部修复

| 编号 | 问题 | 严重级 | 处置 | 修复文件 |
| --- | --- | --- | --- | --- |
| D1 | 离线写入零接线：`syncEngine.enqueue` 无任何调用点，违反 [p2-advanced 需求 5.1](../.kiro/specs/p2-advanced/requirements.md)（离线写操作入队） | P0 | 新增 `utils/offlineSubmit.ts` 统一提交助手，14 个写入页接 `submitOrQueue`（在线直连/离线入队 + toast 明示），8 处强一致操作接 `rejectIfOffline`（离线明确拒绝，不静默） | `src/utils/offlineSubmit.ts`（新增）、材料 3 页 + 现场 4 页 + 财务 9 页 + 审批 3 处 |
| D2 | `uni.showDatePicker` 为不存在的 API（无效语句），H5 日期只能取当天 | P0 | 全端统一改 `<picker mode="date">`（uni-app 内建，三端一致），走查实证可选任意日期并回填 | `src/pages/material/inbound.vue` 等 |
| D3 | `setUserInfo` 未落 storage，冷启动 `offlineCache.sync()` 读到 null → `USER_INFO` 离线缓存永远为空 | P0 | `stores/user.ts`：userInfo 落 storage / 冷启动恢复 / 置空移除 / logout 清理 | `src/stores/user.ts` |
| D4 | 死代码 `utils/offline.ts`：`syncOfflineData` 中 `await uni.request()` 不抛错、无状态码校验，任何响应都计成功出队（**静默丢数据**），且无业务引用 | P0 | 删除 `offline.ts` + `tests/offline.test.ts`（与 `syncEngine` 功能重复，修复将造成双离线系统并存） | 已删除 |

### P0 走查补充发现（H5 真实环境，2026-08-29）—— 已全部修复

| 编号 | 问题 | 严重级 | 处置 | 修复文件 |
| --- | --- | --- | --- | --- |
| W1 | uni-h5 在 `navigator.connection` 存在时只监听其 change 事件，**不捕获 `window` offline/online 事件**，DevTools 断网后 OfflineBanner 不出现 | P0 | `App.vue` 增加 `#ifdef H5` window offline/online 兜底监听（联网恢复时自动 `syncEngine.syncAll()`） | `src/App.vue` |
| W2 | 登录成功后不触发离线缓存初始同步（App.vue 仅冷启动且已有 token 时同步），首次登录后离线缓存缺失 | P1→P0 走查升级 | 登录页密码/短信两处成功路径补 `syncOfflineCacheAfterLogin()`（离线时跳过，失败仅告警不阻断） | `src/pages/login/index.vue` |
| W3 | 「我的」页缺 OfflineBanner，离线状态提示不一致 | P2→走查补齐 | 补 `<OfflineBanner />` | `src/pages/mine/index.vue` |
| W4 | `syncEngine` 将 `UPDATE` 类型重放为 `PUT`，而 `submitInspectionResults` 等后端端点是 `POST`，离线重放必 405 | P0（修复中自查发现） | 该类操作改接 `rejectIfOffline`（离线明示不可用），不伪造重放语义 | `src/pages/site/inspection-detail.vue` 等 |

### P1（多端发布就绪 / 工程门禁）—— 已全部修复

| 编号 | 问题 | 处置 | 修复文件 |
| --- | --- | --- | --- |
| D5 | `BASE_URL` 在 `request.ts` / `syncEngine.ts` 重复定义且仅适配 dev 代理 | 收敛为单一配置模块 `utils/env.ts`：H5 = `/api`（dev 代理 / 生产同源反代）；小程序/原生端读取 `MP_APP_BASE_URL`（默认留空待配置，不编造假地址） | `src/utils/env.ts`（新增）、`request.ts`、`syncEngine.ts` |
| D6 | `manifest.json` 缺定位/相机/相册权限声明，小程序审核与 App 运行时授权受阻 | 补 `mp-weixin.requiredPrivateInfos` + `permission`（scope.userLocation）、`app-plus` Android 权限与 iOS 隐私描述；appid 占位符保留 | `src/manifest.json` |
| D7 | CI 无移动端构建门禁，历史构建失败不会被暴露 | `deploy.yml` frontend-test job（matrix `zw-insight-app`）在 `test:coverage` 后新增 `Build H5 bundle (uni-app)` 步骤，失败即红灯 | `.github/workflows/deploy.yml` |

### P2（本轮不修，另行排期）

| 编号 | 问题 | 说明 |
| --- | --- | --- |
| D8 | api 层 25+ 处 `any` | 需 3 个 api 文件 + 36 页面响应模型对齐，建议契约化改造专项 |
| D9 | `console.log('App Launch')` 等日志残留 | 低优清理 |
| D10 | tabBar 无图标（纯文字） | 体验项，uni-app 允许 |
| D11 | 移动端无 E2E 体系 | 建议 Playwright + H5 通道 + 测试数据治理配套 |

---

## 三、离线提交边界决策（D1 修复设计记录）

| 场景 | 策略 | 理由 |
| --- | --- | --- |
| 单段式表单提交（材料入库/出库/退货、施工日志（无照片）、进度反馈、质量/安全检查、财务 7 页） | `submitOrQueue` 离线入队 | 幂等冲突由后端唯一键/版本号返回 409 → `syncEngine` CONFLICT 链路，用户手动处理，不静默覆盖 |
| 含照片附件的提交（施工日志带照片、整改闭环） | `rejectIfOffline` | 临时文件路径不可序列化，入队后重放必失败 |
| 两段式审批提交（备用金申请、个人报销：先 DRAFT 再提交） | `rejectIfOffline` | 离线入队第一段会造成永久 DRAFT 悬挂单 |
| 审批完成/驳回、检查结果提交 | `rejectIfOffline` | 强一致操作；且后端端点方法与 `syncEngine` 重放方法（UPDATE→PUT）不匹配 |

---

## 四、修复记录（变更原因 / 影响范围 / 回滚方案）

| 修复 | 变更原因 | 影响范围 | 回滚方案 |
| --- | --- | --- | --- |
| 删除 `utils/offline.ts` | 死代码 + 静默丢数据缺陷（D4） | 无（全库无业务引用，仅其自身测试引用） | `git revert` 该删除提交 |
| 新增 `offlineSubmit.ts` + 17 写入页接线 | 需求 5.1 离线写操作入队（D1） | 17 个写入页提交入口；在线路径行为不变（直连原接口），离线路径由「静默失败」变为「入队 + toast 明示」 | 各页面提交函数内 `submitOrQueue` 还原为原直接调用即可，`offlineSubmit.ts` 保留不影响 |
| 日期选择器改 `<picker mode="date">` | `uni.showDatePicker` 不存在（D2） | 材料/财务系列日期字段；三端一致 | 还原模板与对应事件处理 |
| `stores/user.ts` userInfo 持久化 | 冷启动离线缓存依赖（D3） | 登录态生命周期；新增 storage 键 `userInfo` | 删除 storage 读写三处即回退 |
| `App.vue` H5 网络事件兜底 | uni-h5 不监听 window offline/online（W1） | 仅 H5 端（`#ifdef H5`），小程序/原生不受影响 | 删除 `#ifdef H5` 块 |
| 登录页补缓存初始同步 | 首次登录后离线缓存缺失（W2） | 登录成功路径；失败仅告警不阻断登录 | 删除 `syncOfflineCacheAfterLogin` 调用两处 |
| `env.ts` 单一配置源 | BASE_URL 重复定义（D5） | `request.ts` / `syncEngine.ts` 导入来源变化，H5 行为不变 | 还原两处硬编码 `/api` |
| `manifest.json` 权限 | 小程序审核 / App 授权（D6） | 仅构建产物清单，不影响运行逻辑 | `git revert` |
| CI build:h5 门禁 | 构建失败不可见（D7） | `.github/workflows/deploy.yml` frontend-test job | 删除该 step |

---

## 五、安全专项结论

| 项 | 结论 |
| --- | --- |
| Token 存储 | `uni.setStorageSync` 明文。uni-app 常规做法，**风险记录**：XSS 场景可读；H5 生产建议后续评估 HttpOnly Cookie 方案 |
| 401 统一登出 | `request.ts` 拦截器 + `uploadRectificationPhoto` 独立上传路径均已接 401 → 清 token → 跳登录，链路完整 |
| 验证码 | 图形验证码一次性消费（登录失败即刷新新图），已实证正确 |
| HTTPS | 当前联调走 HTTP 代理（`http://129.204.3.200:18080`），**生产部署必须 HTTPS** |
| 小程序合规 | 需登记业务域名白名单 + 申请正式 appid（当前为占位符，未伪造）；`requiredPrivateInfos` 已补齐 |

---

## 六、闭环验收结果（2026-08-29）

1. **单测**: 18 文件 141 用例全绿；覆盖率 879‰ ≥ 基线 879‰（`tests/check-frontend-coverage.mjs` 通过）
2. **构建**: `npm run build:h5` 通过（DONE Build complete）
3. **一致性审计**: 0 Critical / 0 Major，一致率 81.18%，移动端 58 API（无新增 Critical）
4. **H5 走查复验**（租户 9999 隔离账号 `t9999admin`，未污染租户 1 演示数据）：
   - 登录（图形验证码一轮过）→ 首页/审批/入库/施工日志/我的 渲染正常
   - 日期选择器可选任意日期并回填（D2 实证）
   - 断网 → OfflineBanner 出现（W1 实证）→ 入库提交 → 「已存入离线队列」toast → `offline_op_queue` 落 storage
   - 联网 → `syncEngine` 自动重放 POST 200 → 队列清空 → 服务端 3 条记录落库
   - 证据：`mobile-screenshots/walk-01` ~ `walk-09` 系列截图

## 七、发布前置项清单（待人工办理，不在代码内伪造）

- [ ] 微信小程序：申请正式 appid、配置业务域名白名单（必须 HTTPS）
- [ ] 小程序/原生端：配置 `src/utils/env.ts` 中 `MP_APP_BASE_URL` 为真实网关地址
- [ ] H5 生产：确认同源反代 `/api` → 网关，或配置独立域名 + CORS
- [ ] App：按 `manifest.json` 权限描述核对各应用商店审核材料

## 八、走查临时产物说明

- 租户 9999 遗留 1 个测试项目 + 3 条已同步入库记录（隔离租户，按约定无需清理）
- 走查截图保留于 `audit-reports/mobile-screenshots/`（审计证据，非临时文件）
- 根目录无 `_*.png` / `_*.log` / test-results 残留（已自查）
