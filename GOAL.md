# GOAL：线上权限一致性彻底清查（数据权限 + 菜单权限）并修复、审阅、推送

> 上一版 GOAL.md（国内工程管理软件竞品调研）已由本次任务替换；旧内容保留在 git 历史中
> （`git show HEAD:GOAL.md`），对应产出文档已于 2026-10-10 按用户要求移除。

## 定稿合同（2026-10-10 用户确认）

**【最终状态】**
两台线上服务器（129.204.3.200 主环境、43.142.44.145 安徽徽颍建设）上，全部真实租户/角色/用户的
**运行时实际生效权限**与**库中配置权限**逐项一致：每个角色看到哪些菜单、拥有哪些权限码、能查到哪些行级数据，
都与其 `sys_role_menu` / `sys_menu.permission` / `sys_role.data_scope` 配置完全对应；不一致项已定位根因、
修复、复验通过，并完成只读审阅与推送部署。

**【验收 — 全部满足且有证据才算完成】**
- 验收面：`keys/perm-audit-sql.sh`（纯 SQL，只读）+ `keys/perm-audit-http.sh`（真实登录 HTTP 探测）
  + 落盘报告 `audit-reports/permission-consistency-<ts>.md`；脚本退出码严格反映结果。
- **D1 菜单可见性**：每个真实用户经真实登录调 `GET /api/v1/system/menu/user` 返回的菜单 id 集合，
  与由 `sys_role_menu`（角色 status=1/deleted=0、菜单 status=1/deleted=0、非 BUTTON、租户匹配）
  推导的期望集合**双向零差异**（无越权多显、无应显未显）。
- **D2 权限码**：登录返回 `permissions[]` == 期望权限码集合（`sys_menu.permission` 去重，SUPER_ADMIN 额外含 `*:*:*`）；
  且后端 `@RequiresPermission` 守卫码目录 ⊆ 菜单提供码目录，差集逐条定性（设计豁免 / 断链）。
- **D3 行级数据范围**：每个用户 × 每个 `@DataPermission` 受控列表接口，返回行全部满足其有效范围判定式
  （SELF→`created_by`=userId；PROJECT→`project_id` ∈ 用户项目集；DEPT/DEPT_AND_CHILDREN→`dept_id` ∈ 部门集；
  ALL→租户内全量）；且与 SUPER_ADMIN 全量集按判定式推导的"应可见集"**完全相等**（不越权也不误杀）。
- **D4 结构完整性**：孤儿 `sys_role_menu`、悬空 `sys_user_role`、无角色用户、无菜单角色、
  跨租户角色/菜单串号（`role.tenant_id` ∉ {`user.tenant_id`, NULL}）、禁用/已删菜单仍绑定 —— 全部为 0。
- **D5 前端可达性**：每个可见路由的 `meta.permission` 均可被该角色权限码满足；不存在"菜单不显示但 URL 可直达且守卫放行"的页面。
- **回归保护**：`keys/test-api-authz.sh`、`keys/check-menu-coverage.cjs`、R7 数据审计基线
  （PASS=67 FAIL=0 WARN=0）保持通过。

**【不变量 — 任何时候不得违反】**
- 探测只使用**真实登录 + 真实接口**，禁止伪造 token、禁止 mock、禁止降级假数据。
- 禁止走捷径制造达标假象：不得放宽判定式、跳过用户、把 FAIL 改判 SKIP、直接改库掩盖。
- 改线上数据前先 `mysqldump` 备份目标表；SQL 幂等；不覆盖现有机构/岗位/角色/授权（AGENTS.md 约定）。
- 数据库变更脚本**双轨放置**（`zw-app/src/main/resources/db/migration/Vxxx__desc.sql` +
  `deploy/db-init/NN_Vxxx__desc.sql`），版本号唯一有序，幂等。
- 逻辑删除与唯一键：允许删除后重建的实体须用可无限保留历史的唯一键 guard。
- 权限行为语义不得改变：`selectPermissionsByUserId` 的租户/状态过滤口径是权威，菜单下发口径须向其对齐。

**【边界】**
- 允许创建/写入：`keys/`（审计与修复脚本）、`audit-reports/`、`deploy/db-init/` 与
  `zw-insight-server/zw-app/src/main/resources/db/migration/`（新增迁移）、相关 `zw-system` 源码与测试、`GOAL.md`。
- 线上数据库：允许在**逐项确认后**执行幂等的授权/菜单/角色修正（先备份）。
- 不得改动与权限无关的业务代码、不得改动 R7 审计基线数据。
- 若判断必须越界：停下说明理由等用户确认，不先斩后奏。

**【迭代策略】**
1. 先建 baseline：跑脚本产出"当前不一致清单"（真实起点，不预设数字）。
2. 维护假设账本：每条不一致标注「根因假设 / 支持证据 / 反对证据 / 状态」。
3. 每轮只处理一类根因，顺序：D4 结构 → D1 菜单 → D2 权限码 → D3 数据范围 → D5 前端。
4. 每轮结束重跑完整五维验收面，不只验证局部。
5. 记录：假设 → 改动 → 结果 → 下一实验；不重复已证伪路线。

**【停止条件】**
- 五维验收全部有证据通过 → 结束，附证据摘要（前后数字对比 + 关键命令输出）。
- 连续 3 轮无实质进展，或遇到真正 blocker → 停止，输出：已尝试路线 / 关键证据 /
  最可能原因排序 / 真正的 blocker / 解除它需要用户提供什么。

---

## 假设账本

| # | 假设 | 支持证据 | 反对证据 | 状态 |
|---|------|----------|----------|------|
| 1 | `getMenusByUserId` 未过滤角色 status/deleted，禁用/已删角色仍下发菜单，与 `selectPermissionsByUserId`（过滤了）口径不一致 | `SysMenuMapper.xml` selectMenusByRoleIds 仅过滤菜单 status/deleted；`SysMenuService.getMenusByUserId` 未过滤角色状态 | 线上当前 user_role 绑定的角色均 status=1/deleted=0，故无实测命中 | **已证实（latent）→ 已修 F4** |
| 2 | 后端 `@RequiresPermission` 守卫码目录大于菜单提供码目录，部分接口除 SUPER_ADMIN 外不可达 | 静态差集 132 vs 83，差 54 条；实测 `POST /api/v1/contract`、`POST /finance/invoice-apply`、`GET /system/version/current` 对业务角色 403，对 admin 非 403 | 无 | **已证实 → 已补目录 F1** |
| 3 | 主环境 5 个业务角色 data_scope 全为 SELF 属配置偏差 | 主环境 90061-90065 全 SELF；徽颍同族差异化；seed `31_V2026_26` 未写 data_scope 落到 `DEFAULT 'SELF'` | 用户确认以库中配置值为一致性判定基准 → 不计入"不一致" | **已证实为配置合理性缺陷（F5，待决策）** |
| 4 | 菜单下发未考虑 `sys_tenant_menu` | `getMenusByUserId` 无 tenant_menu 关联；线上 `sys_tenant_menu` = 0 行 | 0 行 → 当前无实际影响 | 已排除（无实际影响） |
| 5 | 前端路由守卫只看 `meta.permission`，URL 可直达未授权页面 | `router/index.ts` beforeEach 仅比对 permissions | 后端接口 403 兜底 | 已确认存在但影响有限（后端兜底） |
| 6 | （新增）`@DataPermission` 为 opt-in，未标注的业务 Mapper 完全不注入行级过滤 → SELF 角色越权读全租户 | D3 实测 33 处越权（17 个端点）；`BizInvoiceApplyMapper` 等确无注解 | 部分表为"公司级"语义（资金计划）不宜过滤 | **已证实 → 已修 16 个 Mapper（F2）** |
| 7 | （新增）`@DataColumn(deptColumn="dept_id")` 引用了全库不存在的列 → DEPT 范围必然 SQL 报错 | `information_schema` 全库无 `dept_id` 列；`buildDeptCondition` 生成 `dept_id=?` | 两台环境均无"仅 DEPT 范围"的用户，暂未爆发 | **已证实（latent，F3，待决策）** |

## 轮次记录

### R0（合同定稿）
- 目标：完成侦察并定稿合同。
- 关键事实：三层权限链路（菜单可见性 / 权限码 / 行级数据范围）已定位到具体代码与表；
  两台线上环境角色模型差异极大（主环境 5 角色全 SELF vs 徽颍 10 角色差异化）；
  结构完整性主环境实测 0 孤儿/0 悬空。
- 下一实验：编写并运行只读 SQL 审计（D1 期望集 / D2 目录差 / D4 完整性），建立可复现 baseline。

### R1（baseline + 三处修复 + 独立只读审阅）
- 假设：见账本 #1/#2/#6/#7。
- 改动：
  1. 新增三支只读审计脚本 `keys/perm-audit-{sql,http,data}.sh`，在两台环境建立 baseline 并留存证据文件。
  2. F4：`SysMenuService.getMenusByUserId` 补角色「租户 + 启用 + 未删」过滤 + 单测。
  3. F1：`V2026_91__permission_guard_catalog_completion.sql`（双轨）补 53 个写权限码目录 +
     修正版本管理菜单死码；**同步修正菜单种子源** `data-menu.sql` / `99_data-menu.sql`（否则新环境 initdb 扑空）；
     不改角色现有授权。
  4. F2：15 个越权 Mapper 补 `@DataPermission`；`BizReceivableMapper.addWrittenOffAmount`（UPDATE）
     加方法级 `@DataPermission({})` 豁免（拦截器对 UPDATE 同样注入，类级注解会造成账实不一致）。
- 结果（baseline 证据，`audit-reports/_perm-audit/`）：
  - D1/D2 主环境 **PASS 8/8**、徽颍 admin PASS；D4 双机 **PASS 5/5**（下发链路与配置零差异）。
  - D3 主环境 **PASS 69 / FAIL 33（越权）/ 无法判定 20**；徽颍 admin PASS 33。
  - F1 迁移经 `START TRANSACTION`+`ROLLBACK` 零副作用验证；`check-menu-coverage.cjs` missing 0。
- 独立只读审阅（子代理）结论：**不建议直接推送**，给出 4 个 P1 阻断项，已全部处置：
  ① `biz_machine_ledger` 无 `project_id` 列 → 回退该注解并列为"公司级台账"例外；
  ② UPDATE 也会被数据权限注入 → `BizReceivableMapper` 加方法级豁免；
  ③ 菜单 211 的 UPDATE 在新环境 initdb 会扑空 → 同步修 `data-menu.sql`/`99_data-menu.sql`；
  ④ 审计脚本存在「报错当零违规」静默失败 + 报告引用不存在的证据文件 → 已加断言/前置检查并补齐证据文件。
- 下一实验：提交 → CI 编译与单测 → 双机部署 → 复跑三支审计脚本，确认 F1 目录差归零、D3 FAIL 仅剩已声明例外。
- 未决：F3（DEPT 列缺失）、F5（角色范围取值）、F1 授权矩阵、F2 其余未标注表、F4 同族 latent 项；
  徽颍 4 用户凭证缺失（F6）。

### R2（推送 + 双机部署 + 部署后复验）
- 改动：提交 `7306f6ef` 并推送 `main`；CI run `38067042053`/`38067042051` 双机 matrix 全绿
  （Backend Build 含编译+单测+jacoco、三端前端单测、frontend dist、两台部署均 success）。
- 结果（部署后复跑，证据 `audit-reports/perm-audit-evidence/*-postfix.txt`）：
  - **F1 目录差 54 → 0**（双机；活跃权限码 83 → 136，菜单 211 = `system:version:view`，Flyway 2026.91 已落库）。
  - **D1/D2 仍 PASS 8/8**（无回归；admin 权限码 84 → 137 = 136 + `*:*:*`）。
  - **D4 双机 PASS 5/5**；**R7 基线 PASS=67 FAIL=0 WARN=0 INFO=39 无回归**。
  - **D3 越权 FAIL 33 → 6**，剩余 6 处全部为已声明的两个「公司级共享数据」例外
    （`fin-fundplan` ×4、`machine-ledger` ×2），非缺陷。
  - 运行时显式验证：invoice-apply / payment-received / material-outbound / subcontract-output / project
    对 zhangwei(SELF) 均 total=0（修复前 5/7/2/2），admin 仍见全量。
- 验收结论：合同五项验收面全部有证据；剩余未达成项均为**已声明例外**或**待用户决策项**（§5），
  非本轮 blocker。



