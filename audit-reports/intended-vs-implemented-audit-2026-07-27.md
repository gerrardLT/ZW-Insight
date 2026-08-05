# ZW-Insight 代码审计报告 · 意图 vs 实现

> 方法：`intended-vs-implemented`（比对"文档声明的意图"与"代码实现的现实"）+ `shipping-artifacts`（评估可复核文档的覆盖度）。
> 范围：后端 `zw-insight-server`（Spring Boot 多模块）+ PC 前端 `zw-insight-web`。
> 原则：每条发现都同时给出**文档意图引用**与**代码实现引用**；无法同时引用双边的仅列为"待核实问题"，不计入正式发现。
> 复审说明（2026-07-27 二次运行）：逐条重核代码现实。**原 Critical #2（审批越权）已修复**并降级为正向确认；其余 4 项仍成立。文末新增"七、安全验证测试场景"（test-scenarios）。

---

## 一、结论摘要

| 边界 | 意图来源 | 实现现实 | 是否可跨越 | 级别 |
|------|----------|----------|-----------|------|
| 接口级功能权限（菜单/按钮） | 设计文档 8.2 + 需求 R106/R108 | **机制已落地**：`@RequiresPermission`+`PermissionInterceptor`，已覆盖 system/* 与项目删除/付款提交等高危接口 | 否（高危已强制；其余业务写接口增量铺开） | ✅ 已强制（高危） |
| 审批任务处理人校验 | 需求"审批人节点" + `taskAssignee` 查询语义 | **已修复**：`assertTaskAssignee` 在所有审批入口校验 assignee（SUPER_ADMIN 豁免） | 否 | ✅ 已强制 |
| JWT 密钥保密 | `.env` 注释 + NFR-003 | 密钥**硬编码**在源码与 dev 配置 | 否（Redis 令牌白名单拦截离线伪造） | 🟡 Minor（原判 Critical，已降级） |
| 行级数据权限（按项目/部门） | 全景图"高优先级" + REQ-029 | 覆盖扩至 23 个 Mapper（+labor8/+machine7），非项目维度表已显式豁免 | 否（项目级模块已隔离；非项目维度模块 N/A） | 🟢 部分修复（原 Major） |
| 登录验证码 | dev 配置注释"上线前改回 true" | dev 档 `captcha-enabled: false` | 部分（爆破面扩大） | 🟡 Minor |
| 租户隔离 | 平台设计"租户解析" | `TenantLineInnerInterceptor` 全局注入 | 否 | ✅ 已强制 |
| 逻辑删除 / 乐观锁 | 数据规范 | `BaseEntity` + 全局拦截器 | 否 | ✅ 已强制 |
| `.env` / `.pem` 不入库 | AGENTS.md | `.gitignore` 已生效（已验证） | 否 | ✅ 已强制 |

**总体判断**：租户隔离、逻辑删除、密码加密、登录锁定、**审批处理人校验**、**Redis 令牌白名单**、**接口级功能权限（高危已覆盖）**等"底座"实现扎实。本轮复审相比上一版：原 Critical #2（审批越权）**已修复**；原 Critical #3（JWT 硬编码）经核实 Redis 令牌白名单在位、**降级为 Minor**；原 Critical #1（接口权限）本轮**已落地强制机制并覆盖高危接口**（`@RequiresPermission`+`PermissionInterceptor`，详见发现明细与八、改造记录），其余业务模块写接口增量推进；当前剩余项为数据权限覆盖（Major #4）、验证码（Minor #5）。

---

## 二、意图文档覆盖度（shipping-artifacts 视角）

`shipping-artifacts` 要求的可复核文档集位于 `/documentation/`。当前项目**不存在该目录**，核心文档缺失情况：

| 文档 | 状态 | 现有替代物 |
|------|------|-----------|
| `architecture.md` | ❌ 缺失 | `docs/项目模块全景图.md`、`.kiro/specs/zw-insight-platform/design.md` |
| `flows.md`（授权在运行时的落点） | ❌ 缺失 | 无（审批/付款链路仅散见于各 spec） |
| `permissions.md`（角色×资源×操作矩阵） | ❌ 缺失 | 需求 R4 口径描述，无矩阵 |
| `variables.md`（配置/密钥→风险） | ❌ 缺失 | `deploy/.env`（仅键值，无风险映射） |
| `tests.md`（规则→验证映射） | ❌ 缺失 | `.kiro/specs/full-layer-test-suite/` |

> 按 `intended-vs-implemented` 的前置约定：**文档缺失本身就是第一个发现**。特别是 `permissions.md` 缺失，使得"谁能做什么"缺少权威基线——这正是下述 Critical #1 长期未被发现的结构性原因。

**条件文档**：存在 12 处 `@Scheduled` 定时任务（催办、库存预警、合同到期、备份、租户过期扫描等），故 `cron.md` 适用且应补齐；存在供应商门户公开询价，`automation.md`/对外接口边界文档亦适用。

---

## 三、发现明细

### ✅ 已修复（原 🔴 Critical #1）— 接口级功能权限（高危已覆盖）

> 本轮落地服务端强制机制，已覆盖 system/* 与资金/项目删除等高危接口；其余业务模块写接口属增量铺开范围（见下方优先级表）。

**文档意图**
- `.kiro/specs/zw-insight-platform/design.md` L1023-1047「8.2 接口权限校验」：`PermissionInterceptor.preHandle` → 校验所需权限，无权则拒绝。
- 需求 R106/R108：角色"支持功能权限配置（勾选菜单/按钮）"；前端已建成 `v-permission` 细粒度标识体系，登录响应返回 `permissions` 列表。

**实现现实（已修复）**
- 新增注解 `com.zwinsight.common.security.RequiresPermission`（+`Logical` AND/OR）。
- 新增 `PermissionInterceptor`（`zw-security/.../interceptor/PermissionInterceptor.java`）：`AuthInterceptor` 之后执行（order=1），opt-in（仅注解端点校验），`SUPER_ADMIN` 豁免，按 `sys_menu.permission` 下发的权限集合比对，不满足返回 403；开关 `auth.permission-check-enabled`（默认 true）。
- 已注解高危接口：`SysUser/SysRole/SysMenu/SysOrg/SysPost/SysDict/SysTenant` 全部写接口（如 `resetPassword`→`system:user:reset-pwd`、`updateDataScope`→`system:role:data-scope`）、`ProjectController.delete/batchDelete`→`project:delete`、`PaymentApplyController.submit`→`finance:payment:submit`。
- 权限目录种子：`deploy/db-init/36_V2026_34__p0_function_permission_seed.sql`。

**残留（增量铺开）**：本期仅覆盖高危写接口；project/contract/budget/material/machine/labor/subcontract 等业务模块的常规增删改接口尚未注解，需按优先级逐步补齐（opt-in 机制下未注解即不强制）。

---

### ✅ 已修复（原 🔴 Critical #2）— 审批操作未校验任务处理人

> 本轮复审确认该越权**已被修复**，保留条目用于追溯与回归。

**文档意图**
- `.kiro/specs/zw-insight-platform/requirements.md` L802 审批流"配置节点（审批人/角色/部门负责人）"，审批人是流程节点绑定的特定身份。
- 代码自证意图：`ApprovalService.getMyTodoTasks` 用 `taskAssignee(userId)` 过滤待办——即任务归属特定处理人，隐含"仅该处理人可办理"。

**实现现实（已修复）**
- `zw-workflow/.../service/ApprovalService.java` 新增 `assertTaskAssignee(task, userId)`（L448-465）：先查当前用户角色，含 `SUPER_ADMIN` 直接放行；否则要求 `task.getAssignee()` 非空且等于当前 `userId`，未签收任务抛"请先签收"，不匹配抛 403"无权操作他人审批任务"。
- `complete` L102、`rejectToPrevious` L132、`rejectToStart` L189、`terminate` L244、`transfer` L284、`delegate` L311 **均在取任务后立即调用 `assertTaskAssignee`**。
- `batchApprove` L421-426 循环调用 `complete`，逐条继承该校验。

**残留提示**：`batchApprove` 遇到无权任务会整体抛异常并 `@Transactional` 回滚，"部分成功"语义需产品确认——属功能设计问题，非安全漏洞。建议按下述场景做**回归**防止倒退。

---

### 🟡 Minor #3（复核降级：原判 Critical）— JWT 密钥硬编码

> 本轮复审新增验证：`AuthService.validateToken`（L216-218）= `redisUtils.hasKey("token:"+token) && !isTokenExpired(token)`，登录时 L275 `redisUtils.set("token:"+token,...)` 写入白名单；主系统唯一解析 token 的 `AuthInterceptor` L39 先过 `validateToken` 再取 userId/tenantId，无旁路。**离线伪造的 token 从未写入 Redis，会被直接拒绝**——故"离线伪造任意 token 冒充任意用户"**不成立**，据实由 Critical 降级为 Minor（防御纵深/合规缺陷）。

**文档意图**
- `deploy/.env` 文件头注释：「此文件包含敏感配置，不应纳入版本控制／生产环境请修改为强密码」。
- 需求 NFR-003「敏感数据加密存储」。

**实现现实**
- `JwtUtils.java` L16：`@Value("${jwt.secret:ZwInsight2024SecretKeyForJwtTokenGeneration}")`——**硬编码默认值**；`application-dev.yml` L65 明文同值；无独立生产 profile；`deploy/docker-compose.deploy.yml` L74 用 dev profile 且未注入 `JWT_SECRET`。
- **缓解机制（关键）**：Redis 令牌白名单（见上）使签名合法但非登录签发的 token 一律被拒。

**残留风险（为何仍需修复）**：属防御纵深/合规问题而非可直接利用漏洞——(1) 违反 NFR-003 与密钥管理规范；(2) 一旦未来出现任何"仅验签、不校验白名单"的路径，或攻击者能写 Redis，则立刻升级为全租户沦陷；(3) dev/prod 共用同一硬编码密钥。

**修复**：移除 `jwt.secret` 硬编码默认值（缺失即启动失败 fail-fast），强制从环境变量注入高强度 `JWT_SECRET`；补 `application-prod.yml`。

> 诚实说明：本项严重级别由上一版 Critical **下修为 Minor**，依据是复核了 Redis 令牌白名单这一补偿控制——体现"验证敏感点后再定级"。`deploy/.env`/`keys/*.pem` 经 `.gitignore` 排除并已验证未入库。

---

### 🟢 部分修复（原 🟠 Major #4）— 行级数据权限覆盖扩展

> 本轮将 `@DataPermission` 覆盖从 8 个扩展到 23 个 Mapper（新增 labor 8 + machine 7），覆盖主要项目级业务模块；剩余非项目维度表显式豁免并记录。

**文档意图**
- `docs/项目模块全景图.md` L461：数据权限隔离"影响范围=全部，优先级=高"。
- 需求 REQ-029：项目成员仅可查看/操作所属项目数据。

**实现现实（本轮扩展）**
- 新增覆盖（均含 `project_id`+`created_by`，沿用 `(project_id, created_by, dept_id)` 配置）：
  - labor 8：`BizLaborContract/OutputReport/Payroll/RewardPunish/Roster/Settlement/Team/WorkOrder Mapper`。
  - machine 7：`BizMachineContract/Entry/OilRecord/Repair/Settlement/UsageRecord/WorkLog Mapper`。
- **显式豁免（含理由）**：
  - `BizMachineLedgerMapper`——`biz_machine_ledger` 无 `project_id`（机械台账为租户级资产、非项目维度），加 PROJECT 过滤会引用不存在列报错。
  - `BizMachineWorkSettlementDetailMapper`——明细表无 `project_id`/`created_by`，随主表过滤。
  - `BizMachineWorkSettlementMapper`——含 `getMaxCodeByPrefix` 序号 SELECT，PROJECT 范围下会被过度过滤致编号冲突（全局唯一约束 `uk_settlement_code`）；需方法级数据权限控制后再纳入。
- 未纳入模块（非项目维度，无 `project_id`，不适用项目级隔离）：hr 人事、tender 投标登记、archive 办公用品/车辆、inquiry 询价/报价等——属租户/供应商维度，本期不加。

**配套修正——超管数据范围**：发现 SUPER_ADMIN 角色 `data_scope` 因种子遗漏而为 `SELF`（`99_data-menu.sql` 插入未设该字段取默认值，`13_V2026_10` 又将 NULL 统一置 SELF），而 `ZwDataPermissionHandler` 对超管**无豁免**——超管会被 SELF 行级过滤。随覆盖扩展至 labor/machine，这会让超管"看不到"他人/演示数据。已新增迁移 `deploy/db-init/37_V2026_35__super_admin_data_scope_all.sql` 将 SUPER_ADMIN 置为 `ALL`（处理器对 ALL 返回 null 不过滤），修复该既存问题。

**新发现——dept_id 潜在问题（既存，本轮未扩大）**：全部业务表均**无 `dept_id` 列**，而 `getUserDeptId` 读 `sys_user.org_id`（`DataPermissionDataProviderImpl` L77-83）。若用户具 DEPT/DEPT_AND_CHILDREN 范围且 `org_id` 非空，查询已标注表会拼出 `dept_id = ...` 引用不存在列而报错（`org_id` 非空时不会回退 SELF）。原 8 表同样存在此风险，本轮沿用相同配置**未新增雷区**。**建议**：限制角色可分配数据范围为 ALL/PROJECT/SELF，或为业务表补 dept 映射后再开放 DEPT 范围（单独任务跟进）。

**残留**：hr/tender/archive/inquiry 等非项目维度模块按需评估隔离维度；WorkSettlement 序号查询需框架支持方法级豁免后纳入。

---

### 🟡 Minor #5 — 登录验证码在 dev 档关闭

**文档意图**：`application-dev.yml` L70 注释"联调测试期临时关闭…上线前须改回 true"。
**实现现实**：`application-dev.yml` L71 `auth.captcha-enabled: false`；因无生产 profile，若以 dev 上线则验证码保持关闭。
**影响**：登录爆破防护退化为仅剩账号锁定（L72-75），撞库/爆破面扩大。
**修复**：生产 profile 置 `true`；或在无生产 profile 前提下于部署编排注入 `AUTH_CAPTCHA_ENABLED=true`。

---

## 四、已正确实现的边界（正向确认）

避免只报坏消息，以下边界经代码核实实现可靠：

- **租户隔离**：`MybatisPlusConfig` L77-103 `TenantLineInnerInterceptor` 从 `SecurityContextHolder.getTenantId()`（源自 Token，非请求参数，不可伪造）全局注入 `tenant_id`，忽略表清单（`sys_*`/`act_*` 等）合理有据。✅
- **租户模块访问控制**：`TenantModuleInterceptor` L31-70 按租户已开通模块拦截业务路径——此项设计文档着墨不多，属"实现优于文档"，建议补记入 `architecture.md`。✅
- **逻辑删除 / 乐观锁**：`BaseEntity` L52-59 `@TableLogic`/`@Version` + 全局拦截器，无需手写。✅
- **密码与登录安全**：BCrypt 加密（`AuthService` L38、L186）、失败计数 + 账号/IP 锁定（L164-171、L317-323）、Token 存 Redis 支持主动失效（L217）。✅
- **审批处理人校验**：`ApprovalService.assertTaskAssignee` L448-465，办理/退回/终止/转办/委托全链路校验 assignee，SUPER_ADMIN 豁免。✅（本轮由原 Critical #2 修复而来）
- **Redis 令牌白名单**：`AuthService.validateToken` L216-218 要求 token 命中 `token:{token}`（登录时 L275 写入），使离线伪造/已登出的 token 失效——这是 JWT 对称密钥硬编码之上的关键补偿控制。✅
- **接口级功能权限**：`RequiresPermission` 注解 + `PermissionInterceptor`（`AuthInterceptor` 之后，order=1）校验登录用户权限集合，SUPER_ADMIN 豁免，`auth.permission-check-enabled` 可开关；已覆盖 `system/*`、`project:delete`、`finance:payment:submit` 等高危接口。✅（本轮由原 Critical #1 修复而来）
- **密钥文件不入库**：`.env`/`*.pem` 经 `.gitignore` 排除并已验证生效。✅

---

## 五、修复优先级建议

| 优先级 | 事项 | 对应发现 |
|--------|------|----------|
| P1 | 将 `@RequiresPermission` 增量铺开至其余业务模块写接口（project/contract/budget/material/machine/labor/subcontract 等） | 原 Critical #1 后续 |
| P1 | 数据权限：评估 hr/tender/archive 隔离维度；限制可分配范围为 ALL/PROJECT/SELF 或补 dept 映射 | 原 Major #4 后续 |
| P1 | 生产 profile 开启验证码 | Minor #5 |
| P2 | JWT 密钥改环境变量注入（防御纵深）+ 补生产 profile | Minor #3 |
| P2 | 补齐 `/documentation`（`permissions.md`/`flows.md`/`variables.md`/`cron.md`），更新全景图过时项 | 文档缺口 |

---

## 六、待核实问题（证据未双边闭合，不计入正式发现）

- 供应商门户 `/api/v1/supplier-portal/public/**` 放行范围是否恰当，需对照公开询价业务的数据暴露面确认。
- `batchApprove` 等批量接口在补 assignee 校验后，是否需要区分"部分成功"语义——属功能设计问题，非本次安全审计结论。

---

## 七、安全验证测试场景（test-scenarios）

> 方法：`test-scenarios`。为每条**确认成立**的发现给出"越权复现"（当前应能被攻破，证明漏洞真实）+"修复回归"（补丁后应被拦截）双向用例；对已修复项给出"防倒退回归"。
> 执行约定（AGENTS.md）：集成/接口测试统一使用 `tenant_id=9999` 自动化测试租户，禁止操作真实租户；`@AfterAll` 调 `TestDataCleaner.cleanByTenantId(9999L)` 清理。基座登录能力复用 `keys/verify-base.sh`。
> 角色约定：`低权用户`=仅开通只读/单模块的普通员工账号；`管理员`=SUPER_ADMIN；均在 `tenant_id=9999` 下预置。

### TS-1 垂直越权守卫 —— 低权用户调高危接口应 403（对应 已修复原 Critical #1）

> 状态：本轮机制已落地，`SecurityBoundaryIntegrationTest.ts1_...` 已移除 @Disabled，转为启用的回归守卫。

**测试目标**：验证低权用户绕过前端、直接调用 `system/*` 与项目删除等高危接口时，服务端是否拦截。

**起始条件**
- 后端联调环境可用，`tenant_id=9999` 下预置：低权用户 U_low（无任何 `system:*` 权限）、目标用户 U_target、管理员 U_admin、测试项目 P_9999。
- U_low 通过真实登录接口获取有效 Token（走 `verify-base.sh` 登录）。

**用户角色**：低权用户 U_low

**测试步骤**
1. 以 U_low 的 Token 直接 `PUT /api/v1/system/user/{U_target}/reset-password` 提交新密码 → 观察 HTTP 状态与返回体。
2. 以 U_low 的 Token `PUT /api/v1/system/role/{roleId}/data-scope` 将某角色 dataScope 改为 `ALL` → 观察是否生效。
3. 以 U_low 的 Token `PUT /api/v1/system/user/{U_low}/roles` 给自己追加管理员角色 → 观察是否生效。
4. 以 U_low 的 Token `DELETE /api/v1/project/{P_9999}` → 观察项目是否被删除。

**预期结果（修复后已启用，作为回归守卫）**
- 步骤 1-4 **均返回 403**（`PermissionInterceptor` 在业务逻辑前拦截，低权用户无对应权限且非 SUPER_ADMIN）；同一操作由 U_admin（超管）执行仍 2xx 成功。
- 数据校验：`biz_project` 中 P_9999 的 `deleted` 未被 U_low 置 1；U_target 密码哈希未变。
- 【防倒退】若将 `auth.permission-check-enabled` 置 false 或移除注解，此测试将回到 2xx 而失败，及时暴露强制失效。

### TS-2 JWT 令牌白名单 —— 离线伪造 Token 应被拒（对应 Minor #3，复核降级）

**测试目标**：验证即便用源码公开的 `jwt.secret` 离线签发一枚"签名合法"的 Token，只要它未经登录写入 Redis 白名单，就必须被拒绝——确认 Redis 令牌白名单是硬编码密钥之上的有效补偿控制。

**起始条件**
- 已知源码默认密钥 `ZwInsight2024SecretKeyForJwtTokenGeneration`（`JwtUtils` L16 / `application-dev.yml` L65）。
- 测试用同款 HMAC-SHA256 与该密钥自签一枚 Token（claims：`userId`/`tenantId`/`username`），该 Token 从未经过登录接口、不在 Redis。

**用户角色**：外部攻击者（仅掌握公开密钥，无任何有效会话）

**测试步骤**
1. 用公开密钥离线签发一枚 `tenantId=9999, userId=<任意>` 的合法签名 Token。
2. 携带该伪造 Token 调用任一需登录接口，如 `GET /api/v1/project/page` → 观察 HTTP 状态。

**预期结果（当前即应通过，作为正向回归）**
- 步骤 2 **返回 401**（`validateToken` 因 `hasKey(token:{token})=false` 短路拒绝），证明离线伪造不可直接利用。
- 【防倒退】若未来改为"仅验签、不校验 Redis 白名单"，此测试将变为返回 2xx 而失败，及时暴露回退。
- 【仍建议修复】密钥硬编码属防御纵深/合规问题（见 Minor #3），修复方向为环境变量注入强密钥。

### TS-3 行级数据权限覆盖缺失 —— 跨项目数据泄露（对应 Major #4）

**测试目标**：验证被限定为 `PROJECT`/`SELF` 范围的用户，访问未标注 `@DataPermission` 的模块（machine/labor 等）时能否看到本项目之外的数据。

**起始条件**
- `tenant_id=9999` 下：用户 U_pm 数据范围=`PROJECT`，仅归属项目 P_A；预置 P_A 与 P_B 各自的机械台账/劳务花名册数据。
- 对照组：已标注模块（如 `budget`）在 P_A、P_B 各有数据。

**用户角色**：项目级数据范围用户 U_pm

**测试步骤**
1. 以 U_pm 调 `GET /api/v1/budget/page`（已标注模块）→ 记录返回是否仅含 P_A 数据。
2. 以 U_pm 调机械模块列表接口（未标注模块，如机械台账/工时）→ 观察是否含 P_B 数据。
3. 以 U_pm 调劳务模块列表接口（未标注模块，如花名册/结算）→ 观察是否含 P_B 数据。

**预期结果**
- 对照组步骤 1 **仅返回 P_A 数据**（证明数据权限框架本身生效）。
- 【当前/复现】步骤 2、3 **返回了 P_B 数据**，证明未标注模块存在租户内越权（当前基线：应能复现）。
- 【修复后/回归】为需隔离的业务 Mapper 补 `@DataPermission` 后，步骤 2、3 **仅返回 P_A 数据**；确需豁免的模块须在 `permissions.md` 显式记录并说明理由。

### TS-4 登录验证码在 dev 关闭 —— 爆破面（对应 Minor #5）

**测试目标**：确认当前 dev 档验证码关闭导致登录无需图形码，及修复后应强制校验。

**起始条件**：环境以 `dev`/部署编排启动；`auth.captcha-enabled=false`（`application-dev.yml` L71）。

**用户角色**：未认证访问者

**测试步骤**
1. 不带 captcha 字段调用登录接口，提交正确账号密码 → 观察是否登录成功。
2. 连续多次错误密码 → 观察账号锁定（`lock-max-attempts=5`）是否仍生效。

**预期结果**
- 【当前】步骤 1 **无需验证码即登录成功**（确认关闭状态）；步骤 2 触发账号锁定（说明锁定是唯一剩余防线）。
- 【修复后/回归】生产 profile 或部署编排置 `captcha-enabled=true` 后，步骤 1 缺少有效验证码应**返回校验失败**，无法登录。

### TS-5 审批处理人校验 —— 防倒退回归（对应 已修复原 Critical #2）

**测试目标**：锁定 `assertTaskAssignee` 行为，防止后续重构使审批越权复发。

**起始条件**
- `tenant_id=9999` 下发起一条付款审批流程，当前节点 assignee=U_approver；另有无关用户 U_other 与管理员 U_admin。
- 三者各自持有效 Token；从待办/详情接口取得该 `taskId`。

**用户角色**：无关用户 U_other / 处理人 U_approver / 管理员 U_admin

**测试步骤**
1. U_other `POST /api/v1/workflow/approval/complete`（该 taskId）→ 观察结果。
2. 对未签收的候选任务，U_other 直接 complete → 观察结果。
3. U_approver 对本人 taskId complete → 观察结果。
4. U_admin 对他人 taskId complete → 观察结果。
5. `batch-approve` 传入一条本人任务 + 一条他人任务 → 观察整体结果与事务。

**预期结果**
- 步骤 1 **返回 403「无权操作他人审批任务」**，任务状态不变。
- 步骤 2 **返回 403「该任务尚未签收…」**。
- 步骤 3 **2xx 成功**，`PaymentApply` 正常流转。
- 步骤 4 **2xx 成功**（SUPER_ADMIN 豁免，符合设计）。
- 步骤 5 遇他人任务时**整体抛异常并事务回滚**（本人任务不应被部分提交）；此"部分成功"语义待产品确认，回归只锁定"不得越权"这一安全底线。

---

## 八、改造记录（原 Critical #1 → 接口级功能权限落地）

**变更原因**：后端无接口级功能权限强制（原 Critical #1），高危管理接口仅靠前端隐藏按钮保护，可被任意登录用户绕过 UI 直接调用。

**技术方案**：自定义 `@RequiresPermission` 注解 + `PermissionInterceptor`（RuoYi/Sa-Token 风格，零新依赖），opt-in（仅注解端点强制），SUPER_ADMIN 豁免，复用登录已下发的 `sys_menu.permission` 权限集合。

**影响范围（新增/改动）**：
- 新增：`zw-common/.../common/security/RequiresPermission.java`、`Logical.java`；`zw-security/.../interceptor/PermissionInterceptor.java`；`deploy/db-init/36_V2026_34__p0_function_permission_seed.sql`；`zw-security/.../interceptor/PermissionInterceptorTest.java`。
- 改动：`WebMvcConfig`（注册拦截器 order=1）；`application-dev.yml`（新增 `auth.permission-check-enabled: true`）；`SysUser/SysRole/SysMenu/SysOrg/SysPost/SysDict/SysTenant`、`ProjectController`、`PaymentApplyController` 写接口加注解；解禁 `SecurityBoundaryIntegrationTest.ts1_...`。
- 覆盖范围：本期仅高危写接口；其余业务模块写接口待增量铺开（opt-in 下未注解即不强制）。

**回滚方案**：
- 秒级：置 `auth.permission-check-enabled=false`，全局停用强制（注解与拦截器保留，无副作用）。
- 彻底：从 `WebMvcConfig` 移除 `permissionInterceptor` 注册即可；种子为 `INSERT IGNORE` 幂等，无需回滚。

**豁免校验**：`admin(uid=1)` = `SUPER_ADMIN(rid=1)`，在拦截器中豁免，故现有 admin 登录、各集成测试与真实运维不受影响。
