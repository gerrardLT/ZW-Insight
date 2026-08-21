# 浏览器多角色前端流程走查报告（2026-08-21）

> 方式：内置浏览器（browser-use MCP 直驱）逐账号真实登录线上环境 `http://129.204.3.200:18081`，
> 验证码经截图多模态识别填入，全程真实接口、无 mock、无静默 fallback。
> 覆盖 6 条走查线（T1–T6），对应 7 个账号角色。

## 0. 结论摘要

| 维度 | 结果 |
|------|------|
| 菜单权限收敛（前端） | ✅ 生效：各角色侧栏仅显示授权菜单，与 `GET /system/menu/user` 返回一致 |
| 页面渲染/真实数据加载 | ✅ 各域列表页均渲染且加载真实数据（分页/级联下拉 200） |
| 读接口（GET）权限守卫 | ❌ **系统性缺失**：任意登录用户可读全平台业务分页数据 |
| 写接口（POST/PUT/DELETE）权限守卫 | ⚠️ **不一致**：部分有守卫（system:user、project:delete），部分完全无守卫（notice 全开放、project:save 无守卫） |
| 前端越权路由直开 | ❌ 未拦截：直开 `/system/user`、`/project/create` 等可完整渲染 |

最严重发现：**权限守卫在 Controller 层覆盖不完整且不一致**，前端菜单收敛只是"UI 遮挡"，后端 API 未做同等强制，构成越权读写风险。详见 §2。

---

## 1. 各角色走查明细

### T1 admin（SUPER_ADMIN）
- 19 个一级菜单 / 69 个页面全部渲染正常，108 个 API 全 200。
- 轻微项：`/system/monitor` 系统监控为占位页「待实现（任务 10.3）」。

### T2 admin 核心业务链
- 新建项目 → 投标报名 → 审批办理 5 步全通。
- 发现：① 项目/投标提交后**不生成审批待办**（ACT_RU_TASK 无对应 businessKey，待 DB 复核）；② `BROWSER_TEST_` 残留 3 条（状态机禁止 UI 删除，待清理）；③ 审批待办「发起人」列空。

### T3 zhangwei（PROJECT_MANAGER）
- 菜单收敛生效（11 个一级菜单，无系统管理/投标/基础数据）。
- 首页看板按角色过滤（项目总数 1 vs admin 225）。
- 严重：直开 `/system/user` 完整渲染且 API 200（读越权）。

### T4 wangqiang（FINANCE_STAFF）
- 菜单收敛正确：首页 / 财务管理（开票申请、回款登记、付款申请、收票登记、财务封账）/ 预算管理（预算编制、税率管理、项目最终结算）/ 消息管理。
- 付款申请页渲染；新增弹窗级联表单（项目→合同类型→关联合同→收款单位）真实加载 `project/list`、`supplier/list`（200）。
- 预算编制、财务封账页渲染，财务封账含真实数据（2 条已封账期间）。
- 财务/预算分页接口对 wangqiang 全 200。

### T5 liumin（MATERIAL_STAFF）/ zhaolei（COMMERCE_STAFF）
- liumin 菜单收敛正确：采购管理（采购合同/到货入库/采购结算/领料出库/询价比价/材料调拨）/ 材料库存（库存查询）/ 消息管理。
  - 库存查询页 126 条真实数据；到货入库页渲染。
- zhaolei 菜单收敛正确：合同管理（施工/采购/新增/编辑）/ 采购管理（询价比价）/ 投标管理 / 消息管理。
  - 投标报名页 34 条真实数据。
- 数据清洁问题：列表含 `E2E_TEST_` 前缀残留（库存 126 条、投标 34 条中大量为历史 E2E 残留）。

### T6 sunli / lina（STAFF）
- 菜单收敛最严格：仅 首页 + 消息管理（通知/公告/推送渠道配置/消息中心），与 `menu/user` 返回 2 个根节点一致。
- 消息中心（未读/全部 tab、全部已读）、通知管理、公告管理、推送渠道配置均渲染。
- 越权实证（STAFF）：
  - 直开 `/system/user`：完整渲染 8 个用户 + 编辑/分配角色/重置密码/删除按钮；`GET /api/v1/system/user/page` 200；但 `POST /system/user` 403（写守卫有效）。
  - 直开 `/project/create` 渲染；`POST /api/v1/project` 返回 **400（校验）而非 403** → **项目新建无权限守卫**，STAFF 理论上可建项目。
  - `POST /api/v1/message/notice` 200 并**真实创建记录**（已清理）→ 通知模块读写均无守卫。
  - 批量读探测（STAFF）：`/project/page`、`/budget/page`、`/contract/page`、`/finance/payment-apply/page`、`/system/role`、`/basedata/material` 全 200。

---

## 2. 问题清单（按严重度）

### Critical
1. **读接口系统性无权限守卫**。GET 分页/详情端点普遍缺 `@RequiresPermission`，任意登录角色可读全平台业务数据（项目/预算/合同/财务/角色/用户/基础数据）。
   - 代码实证：`SysUserController` GET（page/getById/export）无注解；仅写操作有守卫。
2. **部分写接口无守卫**。`NoticeController` 全部端点（含 POST save/publish）无任何注解，STAFF 可真实创建/发布通知；`ProjectController.save`（POST /project）无注解（仅 delete 有 `project:delete`）。

### Major
3. **前端越权路由未拦截**。无菜单授权的路由（`/system/user`、`/project/create`）直开可完整渲染，仅靠后端写守卫兜底，读数据已泄露。
4. **前后端权限模型不一致**。前端菜单收敛（UI）与后端 API 强制（缺失）不同步，形成"UI 遮挡、API 敞开"。

### Minor
5. 项目/投标提交不生成审批待办（待 DB 复核 ACT_RU_TASK）。
6. `/system/monitor` 占位页待实现。
7. 审批待办「发起人」列空。
8. 线上演示租户含 `E2E_TEST_`/`BROWSER_TEST_` 残留数据，需清理。

---

## 3. 处置建议

1. **统一守卫策略（Critical 1/2）**：为所有业务 Controller 的读/写端点补齐 `@RequiresPermission`，或引入"默认拒绝 + 白名单"机制；优先覆盖 system/user、project、notice、finance、budget、contract。
2. **路由守卫（Major 3）**：前端路由 `meta.permission` + 全局前置守卫，无授权路由跳 403 页，与菜单收敛同源。
3. **数据清理（Minor 8）**：按租户 1 清理 `E2E_TEST_`/`BROWSER_TEST_` 前缀业务记录。
4. 待办生成（Minor 5）与占位页（Minor 6）转入对应 spec 任务跟踪。

> 截图证据（登录页验证码、sunli 越权 /system/user、sunli /project/create、wangqiang 财务封账等）已于走查时捕获，按临时产物策略任务结束即清理，不复留于工作区；上述结论均可按 §1 步骤复现。
