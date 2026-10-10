# 线上权限一致性清查报告（数据权限 + 菜单权限）

- 日期：2026-10-10（2026-10-11 补充审阅修订）
- 范围：主环境 `129.204.3.200`、安徽徽颍建设 `43.142.44.145`（均为生产库 `zw_insight`）
- 方法：只读 SQL（`keys/perm-audit-sql.sh`）+ 真实登录真实接口（`keys/perm-audit-http.sh`、`keys/perm-audit-data.sh`）
- 判定基准（用户 2026-10-10 确认）：以**库中 `sys_role.data_scope` 配置值**为准判定运行时一致性
- 原则：不伪造 token、不 mock、不猜密码、403 与空集严格区分、只读探测

---

## 0. 权限链路（三层，逐一对应到代码与表）

| 层 | 配置来源 | 运行时代码 | 消费方 |
|---|---|---|---|
| ① 菜单可见性 | `sys_menu` ⋈ `sys_role_menu` | `SysMenuService.getMenusByUserId` → `GET /api/v1/system/menu/user` | 前端侧边栏 / 动态路由 |
| ② 权限码 | `sys_menu.permission` ⋈ `sys_role_menu` | `SysUserMapper.selectPermissionsByUserId` → 登录响应 `permissions[]` | 后端 `PermissionInterceptor`（源码 297 处 `@RequiresPermission` 注解、**132 个不同权限码**、SUPER_ADMIN 豁免）+ 前端路由守卫 |
| ③ 行级数据范围 | `sys_role.data_scope` | `ZwDataPermissionHandler` 注入 WHERE，仅对标注 `@DataPermission` 的 Mapper 生效 | 列表/详情查询 |

数据范围优先级：`ALL(5) > DEPT_AND_CHILDREN(4) > DEPT(3) > PROJECT(2) > SELF(1)`，多角色取最高。

> 说明：`@DataPermission` 为 **opt-in**，且 MyBatis-Plus 的 `DataPermissionInterceptor` 对
> **SELECT / UPDATE / DELETE 均注入**（本地 jar `mybatis-plus-extension-3.5.5` 经 `javap` 核实存在
> `beforePrepare`/`processUpdate`/`processDelete`）——这一点对 F2 的修法有直接影响（见 §3 F2）。

---

## 1. 验收结论总览

| 维度 | 主环境 129.204.3.200 | 徽颍 43.142.44.145 |
|---|---|---|
| D1 菜单下发 == 配置 | **PASS 8/8** | PASS（admin）；4 个真实用户**凭证未知**无法验证 |
| D2 权限码下发 == 配置 | **PASS 8/8** | PASS（admin） |
| D4 结构完整性 | **PASS 5/5** | **PASS 5/5** |
| D3 行级数据范围 | **PASS 69 / FAIL 33 / 无法判定 20** | PASS 33（admin，ALL）；4 用户凭证缺失 |
| D2-b 权限码目录完备性 | **FAIL：54 个守卫码无菜单承载** | 同（两侧 `sys_menu` 完全一致） |
| 数据范围 DEPT 可用性 | **FAIL（latent）：全库无 `dept_id` 列** | 同 |

**一句话结论**：**「配置 → 运行时」的下发链路（菜单、权限码）在两台环境上完全一致，零差异**；
但**配置本身与实现存在三处结构性缺陷**（行级越权、DEPT 不可用、权限码目录不全），
以及主环境一处配置合理性缺陷（业务角色全 SELF）。

---

## 2. 通过项（有证据）

### D1/D2 菜单与权限码下发口径一致
主环境 8 个真实用户（`admin`、`zhangwei`、`lina`、`wangqiang`、`liumin`、`chengang`、`zhaolei`、`sunli`）逐个真实登录，
`GET /api/v1/system/menu/user` 返回的菜单 id 集合与登录响应 `permissions[]`，
与由 `sys_role_menu` ⋈ `sys_menu` 推导的期望集合**双向零差异**（无越权多显、无应显未显）。

原始证据（均在 `audit-reports/perm-audit-evidence/`）：
`s1-sql-baseline.txt`、`s1-http-baseline.txt`、`s1-data-baseline.txt`、
`s2-sql-baseline.txt`、`s2-http-baseline.txt`、`s2-data-baseline.txt`。

### D4 结构完整性
| 检查 | 主环境 | 徽颍 |
|---|---|---|
| 孤儿 `sys_role_menu`（菜单不存在/已删） | 0 | 0 |
| 悬空 `sys_user_role`（角色不存在/已删） | 0 | 0 |
| 禁用/已删菜单仍被角色绑定 | 0 | 0 |
| 跨租户角色绑定（`role.tenant_id` 与 `user.tenant_id` 不符） | 0 | 0 |
| 同租户角色码重复 | 0 | 0 |

登记（非缺陷）：主环境测试租户 9999 有 23 个 L4 压测残留用户（`l4entry*`）无角色；徽颍 1 个。
两侧测试租户的 `T9999_LIMITED` / `T9999_REGRESSION` 角色无菜单（负向测试用，预期）。

### D5 前端可达性
- 前端路由守卫（`zw-insight-web/src/router/index.ts` 的 `beforeEach`）按 `meta.permission` 比对
  用户权限码，缺失则跳 `/403`；`*:*:*`（SUPER_ADMIN）直通。
- 侧边栏由 `GET /api/v1/system/menu/user` 驱动，与守卫同源（同一份 `sys_role_menu`），
  故不存在「菜单可见但守卫拒绝」的分叉。
- `keys/check-menu-coverage.cjs`：前端可见路由 118 条 vs 后端菜单路径 125 条，**missing 0**。
- **已知局限（非本轮修复项）**：守卫只看 `meta.permission`，不校验「该菜单是否对该用户可见」，
  因此「菜单不显示但 URL 可直达」在权限码满足时不会被前端拦住——由后端 `@RequiresPermission` 兜底 403。
  本轮未改动前端；如需前端也做菜单可见性校验，属独立需求。

---

## 3. 缺陷清单

### F1【P1】权限码目录不完备：54 个后端守卫码无任何菜单承载

后端 `@RequiresPermission` 共要求 **132** 个权限码，线上活跃菜单只提供 **83** 个，差集 **54 个**（两台环境完全相同）。
由于 `selectPermissionsByUserId` 只从 `sys_menu.permission` 取码，**这 54 个码无法被任何非 SUPER_ADMIN 角色获得**，
管理员也无法通过「角色管理」授予（菜单目录里根本没有这些码）。

实证（主环境，真实登录）：

| 调用 | zhangwei（PROJECT_MANAGER） | admin |
|---|---|---|
| `POST /api/v1/contract`（需 `contract:contract:add`） | **403** | 400（校验失败，权限已过） |
| `POST /api/v1/finance/invoice-apply`（需 `finance:invoiceapply:add`） | **403** | 400 |
| `GET /api/v1/system/version/current`（需 `system:version:view`） | **403** | 200 |

差集 54 个码（53 个写操作码 + `system:version:view`）：
`budget:add`、`budget:budget:{add,edit,delete,submit}`、`budget:budgetchange:{add,edit,delete,submit,withdraw}`、
`budget:budgetcontrolconfig:{add,edit,delete}`、`budget:costaccount:update`、`budget:costsubcategory:{add,edit,delete}`、
`contract:bom:{add,edit,delete,import}`、`contract:boq:{upload,delete}`、`contract:changevisa:{add,submit}`、
`contract:contract:{add,edit,delete,submit}`、`contract:othercontract:{add,edit,delete}`、
`contract:outputreport:{add,delete,submit}`、`contract:quantitylist:{add,edit,delete,import}`、
`contract:settlement:{add,submit}`、`finance:bankaccount:{add,edit,delete}`、`finance:financelock:{create,unlock}`、
`finance:invoiceapply:{add,edit,delete,submit}`、`finance:taxrate:{add,edit,delete}`、`system:version:view`

附带的**死码**问题：「版本管理」菜单（id=211）登记的 `system:version:list` **全库无任何接口使用**（0 处），
而 `VersionController` 类级要求的是 `system:version:view` —— 该页面因此对所有人（除超管）恒 403。

> 说明：这是「目录完备性」缺陷，不是「下发不一致」——现有授权矩阵本身与运行时装一致（见 §2）。
> 是否把这些写权限**授予**业务角色属产品授权策略，本次不擅自变更（见 §5）。

### F2【P0】行级数据范围越权：业务列表端点未标注 `@DataPermission`

未标注 `@DataPermission` 的 Mapper 完全不注入过滤条件，于是 `data_scope=SELF` 的角色能看到**全租户**数据。
实测（主环境真实登录，SELF 角色返回了非本人创建的行）：

| 端点 | 后端 Mapper | 实测越权行数 | 处置 |
|---|---|---|---|
| `GET /api/v1/finance/invoice-apply/page` | `BizInvoiceApplyMapper` | 5 | **已修** |
| `GET /api/v1/finance/payment-received/page` | `BizPaymentReceivedMapper` | 7 | **已修** |
| `GET /api/v1/finance/retention/page` | `BizRetentionMoneyMapper` | 1 | **已修** |
| `GET /api/v1/finance/security-bond/page` | `BizSecurityBondMapper` | 无法判定（响应缺 `createdBy`） | **已修** |
| `GET /api/v1/finance/receivable/page` | `BizReceivableMapper` | 无法判定 | **已修** |
| `GET /api/v1/finance/lock/page` | `BizFinanceLockMapper` | 无法判定 | **已修** |
| `GET /api/v1/finance/bill/page` | `BizBillMapper` | 无法判定 | **已修** |
| `GET /api/v1/finance/wage-account/page` | `BizWageSpecialAccountMapper` | 无法判定 | **已修** |
| `GET /api/v1/material/outbound/page` | `BizMaterialOutboundMapper` | 2 | **已修** |
| `GET /api/v1/material/inventory/page` | `BizMaterialInventoryMapper` | 1 | **已修** |
| `GET /api/v1/purchase/settlement/page` | `BizPurchaseSettlementMapper` | 4 | **已修** |
| `GET /api/v1/site/completion/page` | `BizCompletionAcceptanceMapper` | 1 | **已修** |
| `GET /api/v1/site/construction-log/page` | `BizConstructionLogMapper` | 3 | **已修** |
| `GET /api/v1/subcontract/output/page` | `BizSubcontractOutputReportMapper` | 2 | **已修** |
| `GET /api/v1/tender/register/page` | `BizTenderRegisterMapper` | 1 | **已修** |
| `GET /api/v1/machine/ledger/page` | `BizMachineLedgerMapper` | 52 | **不加过滤（见下）** |

合计 33 处实测越权（12 个端点有真实数据命中 + 5 个端点响应缺字段无法行级判定，但 Mapper 同样未标注）。

**两处刻意不加行级过滤的表（属"公司级共享数据"，非越权）**：
- `biz_machine_ledger`（机械台账）：表结构**无 `project_id`**（仅有 `current_project` 文本列），
  是公司级设备台账；按创建人过滤会让台账对除登记人外所有人不可见，语义错误。**审阅阶段已回退该注解**。
- `biz_fund_monthly_plan`（月度资金计划）：含 `project_id IS NULL` 的公司级行，按 project 过滤会误伤公司级计划。

另有约 40 张含 `project_id` 的表其 Mapper 亦未标注（当前端点无业务角色可达或表内无数据，属潜在同类风险）。

### F3【P0 latent】数据范围 `DEPT` / `DEPT_AND_CHILDREN` 实现断裂

全库 **没有任何一张表存在 `dept_id` 列**（`information_schema.columns` 全库扫描为 0 行），
但所有 `@DataPermission` 都声明 `deptColumn = "dept_id"`（`@DataColumn` 默认值即 `dept_id`）。
`ZwDataPermissionHandler.buildDeptCondition` 会生成 `dept_id = ?` / `dept_id IN (...)` → MySQL 1054 → 接口 500。

当前未爆发的唯一原因是**多角色取最高优先级**掩盖：徽颍虽有 `DEPT_MANAGER(DEPT_AND_CHILDREN)`、
`PURCHASE_STAFF(DEPT)`，但 4 个真实用户都同时持有 `ALL` 范围角色，故实际按 ALL 放行。
一旦某用户被单独授予 DEPT 范围角色，其所有 `@DataPermission` 列表页将 500。

### F4【P2】菜单下发口径与权限码口径不一致（latent）

`SysMenuService.getMenusByUserId` 取用户角色时**未过滤** `sys_role.status` / `deleted`，
而 `SysUserMapper.selectPermissionsByUserId` **已过滤**。
后果：用户被绑定到「已停用/已逻辑删除」角色时，该角色仍下发菜单（页面可见），但不参与权限码计算（接口 403）。
当前两台环境绑定的角色均为 `status=1, deleted=0`，故未实际命中。

> 审阅补充：权限码口径还额外过滤 `u.status=1` 与菜单侧租户 `m.tenant_id`；本轮 F4 只对齐了
> **角色层**（租户 + status + deleted）。菜单层租户过滤与 `u.status` 属同族 latent 项，见 §5。

### F5【P1 配置合理性】主环境 5 个业务角色 `data_scope` 全为 `SELF`

主环境 `PROJECT_MANAGER / FINANCE_STAFF / MATERIAL_STAFF / COMMERCE_STAFF / STAFF`（90061-90065）全部 `SELF`；
徽颍同族角色为差异化（`PROJECT_MANAGER=PROJECT`、`FINANCE_STAFF=ALL`、`PURCHASE_STAFF=DEPT`、`STAFF=SELF`）。

根因：`deploy/db-init/31_V2026_26__seed_demo_data.sql` 建这 5 个角色时**未写 `data_scope` 列**，
落到 `V2026_10` 的 `DEFAULT 'SELF'`。

后果（实测）：所有演示业务单据 `created_by = 1`（admin），因此 5 个业务角色在**所有** `@DataPermission` 表上可见行数均为 **0**
——他们拥有 44/41/13/20/2 个业务菜单，点进去却全是空列表。

按用户确认的判定基准（以配置值为准），这**不构成"下发不一致"**，但属配置合理性问题，见 §5。

### F6 徽颍 4 个真实用户凭证未知（阻塞项）

`wangting / wangshuangtao / wanghaiyang / biancongyou` 密码未知（客户环境，**不猜密码、不重置**），
故其 D1/D2/D3 的 HTTP 维度无法验证，已如实登记（`s2-http-baseline.txt` 显示 4 个 FAIL 为登录失败）。
其期望集合已由 SQL 推导（`s2-sql-baseline.txt`），且同一份后端代码在主环境 8 个用户上已验证「下发 == 配置」。

---

## 4. 修复实施（本次执行）

| ID | 修复方式 | 文件 |
|---|---|---|
| F4 | 代码：`getMenusByUserId` 补「用户所属租户内、启用未删」角色过滤，与 `selectPermissionsByUserId` 角色层口径对齐；新增 1 个单测（停用角色不下发菜单） | `zw-system/.../SysMenuService.java`、`SysMenuServiceTest.java` |
| F1 | 迁移：`V2026_91__permission_guard_catalog_completion.sql`（双轨：`db/migration/` + `deploy/db-init/93_`）。补 53 个写权限码的 BUTTON 目录；把「版本管理」菜单(211) 的死码 `system:version:list` 对齐为 `system:version:view`，**并同步修正菜单种子源** `data-menu.sql` 与 `99_data-menu.sql`（否则新环境 initdb 时 93_ 先于 99_ 执行，UPDATE 扑空 → 复现 V2026_77 记录的同类事故） | 2 个迁移 + 2 个菜单种子 |
| F2 | 代码：为 **15 个**越权 Mapper 补 `@DataPermission(@DataColumn(projectColumn="project_id", userColumn="created_by", deptColumn="dept_id"))`；`BizReceivableMapper.addWrittenOffAmount`（UPDATE）加**方法级 `@DataPermission({})` 豁免**——因拦截器对 UPDATE 同样注入，类级注解会让核销语句被追加 `AND created_by=?`，而 `ReceivableService` 不校验返回行数，将造成「台账静默未更新、核销明细已落库」的账实不一致 | 15 个 Mapper（finance 8 / material 2 / purchase 1 / site 2 / subcontract 1 / tender 1）+ `BizReceivableMapper` 方法级豁免 |

**迁移零副作用验证**（`START TRANSACTION` + `ROLLBACK`）：新增 `sys_menu` 53 行、`sys_role_menu` 53 行、
菜单 211 权限码更新为 `system:version:view`；回滚后各表复核为 0 行 / 原值，无残留。

**回归检查**：`node keys/check-menu-coverage.cjs` → `missing 0`。
**审计脚本加固**：`perm-audit-sql.sh` 的 `chk()` 增加「查询失败/非数值即 FAIL」+ DB 连通性前置断言，
并以**负向自检**验证（把断言 SQL 改成引用不存在列 → 输出 `[FAIL] ... 查询失败或返回非数值（rc=1）`，
不再出现「报错 → 0 → PASS」）；`perm-audit-http.sh` 增加「期望集合为空即中止」；
`perm-audit-data.sh` 把 HTTP 000 单列为 ERROR（不计 PASS）。

**授权影响声明（修正）**：本次**未给任何业务角色新增授权**。唯一会改变既有角色有效权限码的是
菜单 211 的码对齐——经核对，绑定 211 的角色为主环境 `SUPER_ADMIN`、徽颍 `SUPER_ADMIN` 与
`admin`（`biancongyou` 持有）。即：徽颍 `biancongyou` 部署后将**获得**只读的 `system:version:view`
（其本就持有该菜单，属修正而非扩权）。

未实施（待决策，见 §5）：F3、F5、F1 的授权矩阵、F2 的公司级表与其余未标注 Mapper。

---

## 5. 待用户决策项

1. **F1 授权矩阵**：54 个写权限码补入目录后，是否按「持有 `{module}:view` 即授予该模块写权限」批量授权？
   还是仅项目经理/商务保留写入、其余角色只读？（`V2026_45_2` 的注释显示原意是"写权限仅超管 + 项目立项/编辑给 PM/商务"）
2. **F3 DEPT 范围**：三选一——(a) 为相关表补 `dept_id` 列并由项目归属回填；(b) 把 DEPT 语义改为"经项目→部门"派生；
   (c) 从 `data_scope` 合法值中移除 DEPT/DEPT_AND_CHILDREN（则徽颍现有角色需改配）。
3. **F5 主环境角色范围**：是否把 5 个演示角色改为 `PROJECT_MANAGER=PROJECT / FINANCE_STAFF=ALL / MATERIAL_STAFF=DEPT /
   COMMERCE_STAFF=ALL / STAFF=SELF`（对齐徽颍语义）？改后演示账号才能看到数据。
4. **F2 公司级表**：`biz_machine_ledger`（无 project_id）、`biz_fund_monthly_plan`（含公司级行）
   已确认**不加**行级过滤；是否认可？其余约 40 张未标注表是否要按模块补齐（需逐表确认业务语义）？
5. **F4 同族 latent 项**：是否一并把菜单侧租户过滤（`m.tenant_id`）与 `u.status=1` 对齐到权限码口径？
6. **F6**：如需完整验证徽颍 4 个用户，请提供测试口令或允许在测试租户建同构探针账号。

---

## 6. 复现命令

```bash
# 只读 SQL 面（结构完整性 + 期望集合；DB 不可达会直接 FATAL 退出）
ssh -i keys/zwinsight.pem root@129.204.3.200 "bash /root/zwi-deploy/perm-audit-sql.sh"
# 真实登录：菜单与权限码下发 vs 配置
ssh -i keys/zwinsight.pem root@129.204.3.200 "bash /root/zwi-deploy/perm-audit-http.sh --tenant 1"
# 真实登录：行级数据范围
ssh -i keys/zwinsight.pem root@129.204.3.200 "bash /root/zwi-deploy/perm-audit-data.sh --tenant 1"
```

（徽颍同上，替换私钥与主机；脚本在 `keys/`，需先 `scp` 到 `/root/zwi-deploy/`。）

---

## 7. 部署后复验（待回填）

- [ ] CI 编译 + 单测通过
- [ ] 双机部署完成
- [ ] `perm-audit-sql.sh`：F1 目录差从 54 → 0
- [ ] `perm-audit-http.sh`：D1/D2 仍 PASS（菜单/权限码下发未被破坏）
- [ ] `perm-audit-data.sh`：D3 FAIL 从 33 → 仅剩已声明的 `machine-ledger` 例外（2 处）
- [ ] `keys/audit-data.ps1` R7 基线不回归（PASS=67 FAIL=0）
