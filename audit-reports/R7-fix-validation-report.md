# R7-02 / R7-03 修复与垃圾数据清理 —— 验证报告

**执行日期**：2026-09-18
**执行范围**：生产联调库 `root@129.204.3.200` / 容器 `zwi-mysql` / 库 `zw_insight`
**最终结果**：数据审计 Round 7 **PASS=65 FAIL=0 WARN=0 INFO=40**（修复前 PASS=50 FAIL=11 WARN=5）

---

## 一、结论速览

| 指标 | 修复前 | 修复后 |
|------|--------|--------|
| 审计 FAIL | **11** | **0** |
| 审计 WARN | **5** | **0** |
| 审计 PASS | 50 | 65 |
| `biz_project` 存活数 | 38（4 种子 + 34 垃圾） | **4**（仅演示种子） |
| `sys_user_project` | 总 1042 / 项目孤儿 628 | **总 8 / 孤儿 0** |
| 施工合同引用已删除项目 | 49 | **0** |
| 库存负值 | 4 | **0** |
| E2E_TEST_ 项目残留 | 7 | **0** |
| E2E_TEST_ 询价残留 | 3 | **0** |
| 时间戳后缀项目残留 | 21 | **0** |
| API测试项目残留 | 11 | **0** |
| `total_expense` 勾稽 | 4/38 MISMATCH | **0/4 MISMATCH** |
| `total_income` 勾稽 | 2/38 MISMATCH | **0/4 MISMATCH** |
| `cumulative_output` 勾稽 | 2/38 MISMATCH | **0/4 MISMATCH** |
| 支出合同 `cumulative_paid` 勾稽 | 6/34 MISMATCH（4 类合计） | **0 MISMATCH** |
| 支出合同 `cumulative_settlement` 勾稽 | 5/34 MISMATCH（3 类合计） | **0 MISMATCH** |
| 施工合同开票/收款勾稽 | 2/61 MISMATCH | **0/4 MISMATCH** |

物理删除合计 **3106 行**（D-0/D-1 3100 行 + D-2 询价家族 6 行），执行前均已 mysqldump 全量备份并校验。

---

## 二、阶段 A：项目删除级联（R7-02 代码层止血）

### 2.1 计划方案不可行，改用事件机制

计划原文要求在 `ProjectService.delete` 中注入 20+ 个跨模块 Service。**该方案技术上不可行**：

- `zw-project/pom.xml` 仅依赖 zw-common / zw-security / zw-file / zw-workflow
- 反向 Grep 证实 **14 个模块依赖 `zw-insight-project`**（budget / material / contract / app / tender / purchase / archive / dashboard / subcontract / machine / site / labor / finance）
- 故 zw-project 反向注入这些模块必然形成 Maven 循环依赖，编译失败

**采用方案**（用户 2026-09-18 决策）：Spring 事件解耦，契约类置于 zw-common 共享内核。
该做法与仓库既有惯例一致——`LoginLogListener` 注释即写明「security → system 为反向依赖，
通过 Spring 事件解耦」，`ChangeEventApprovedEvent` 亦有「为什么放在 zw-common 而不是
zw-contract」的说明。

### 2.2 实现结构

```
ProjectService.delete(id)                     ← @Transactional(rollbackFor = Exception.class)
  ├─ 前置校验（原有逻辑不变）
  │    ├─ 项目存在性
  │    ├─ 仅 DRAFT 可删（E2eTestGuard 标记数据旁路）
  │    └─ countTenderRegisters > 0 → 拦截
  ├─ 级联第 1 层：本模块自有 3 表（直接调 Mapper）
  │    ├─ biz_project_member      逻辑删除
  │    ├─ biz_project_wbs_node    逻辑删除
  │    └─ sys_user_project        物理删除（该表无 deleted 列）
  ├─ projectMapper.deleteById(id)
  └─ 级联第 2 层：publishEvent(ProjectDeletedEvent)
       └─ 11 个模块的 Listener 同事务清理各自表（异常传播 → 整体回滚）
```

**69 张含 `project_id` 列的表全部覆盖**（由 `information_schema` 全量枚举，非人工列举）：

| 模块 | 表数 | Listener |
|------|-----|----------|
| zw-project | 3 | 直接调 Mapper（自有表） |
| zw-contract | 8 | `ContractProjectCascadeCleanupListener` |
| zw-material | 6 | `MaterialProjectCascadeCleanupListener` |
| zw-machine | 8 | `MachineProjectCascadeCleanupListener` |
| zw-labor | 8 | `LaborProjectCascadeCleanupListener` |
| zw-finance | 11 | `FinanceProjectCascadeCleanupListener` |
| zw-purchase | 2 | `PurchaseProjectCascadeCleanupListener` |
| zw-subcontract | 4 | `SubcontractProjectCascadeCleanupListener` |
| zw-budget | 7 | `BudgetProjectCascadeCleanupListener` |
| zw-site | 7 | `SiteProjectCascadeCleanupListener` |
| zw-tender | 4 | `TenderProjectCascadeCleanupListener` |
| zw-file | 1 | `FileProjectCascadeCleanupListener` |
| **合计** | **69** | |

### 2.3 三个关键实现决策

**① 类名必须带模块前缀（曾是启动阻断级 BUG）**

11 个 Listener 若同名 `ProjectCascadeCleanupListener`，启动时必抛
`ConflictingBeanDefinitionException`：`ZwInsightApplication` 用
`scanBasePackages="com.zwinsight"` + 默认 `AnnotationBeanNameGenerator`（按短类名生成 bean 名），
而 `@MapperScan` 里的 `FullyQualifiedAnnotationBeanNameGenerator` **只管 Mapper、管不到组件扫描**。

仓库对偶发重名（两个 `TemplateService`、两个 `InspectionSchemeService`）的既有做法是加显式 bean 名
（`@Service("fileTemplateService")`）；但本 Listener 是「同一职责在每模块各一份」的家族，
模块名就是其语义区别项，故直接把模块名编进类名，同时避免 11 个同名文件在 IDE 与异常栈里无法区分。

**② 用字符串列名 `QueryWrapper` 而非 `LambdaQueryWrapper`**

只依赖「表有 `project_id` 列」这一已由 `information_schema` 全量核实的事实，
不依赖 68 个实体的 getter 签名。附带好处：列名无需 `TableInfo` 即可解析，
单测不需要 `TableInfoHelper.initTableInfo` 预热（对比 `ReserveFundApplyServiceTest`）。

**③ 不吞异常**

与 `LoginLogListener`（日志失败不影响主流程）相反，级联清理失败**必须**传播，
让 `ProjectService.delete` 的整体事务回滚，杜绝「项目删了但子表留着」的半成品状态
——那正是 R7-02 要修的缺陷本身。

### 2.4 验证

- `mvn -o -DskipTests test-compile`：**23 个模块全部 BUILD SUCCESS**
- 单元测试：`ProjectServiceTest` 35/35、`ProjectMutationTest` 9/9、
  `ContractProjectCascadeCleanupListenerTest` 3/3 —— **47 用例全绿**
- 静态校验：全仓 409 个 stereotype bean 扫描，11 个 Listener 名称两两唯一

> 注：本地无 Docker，`@SpringBootTest` 全上下文测试（`BaseIntegrationTest`）会被
> `@EnabledIfDockerAvailable` 跳过，故 bean 名冲突改用静态扫描验证；CI 的
> `mvn -B -T 1C clean verify` 会真实启动上下文做最终把关。

### 2.5 顺带修复的两个既有测试

`ProjectService` 构造函数新增 4 个依赖后：
- `ProjectMutationTest` L80 显式 `new ProjectService(4 args)` → **编译失败**，已补齐 8 参
- `ProjectServiceTest` 用 `@InjectMocks` → 新依赖被注入 `null`，delete 用例 **运行时 NPE**，已补 4 个 `@Mock`

---

## 三、阶段 D：垃圾数据大扫除

脚本：`keys/cleanup-garbage-data.sh`（默认 dry-run，`--execute` 才写库）

### 3.1 安全措施

| 措施 | 说明 |
|------|------|
| 默认只读 | 未显式传 `--execute` 绝不写库 |
| 强制备份 | execute 模式先 mysqldump，校验「体积 > 1MB 且 CREATE TABLE ≥ 200」不通过即中止 |
| 不变量① | 测试项目判定式命中任一演示种子（90001-90004）→ 立即 `exit 3` |
| 不变量② | 逐分支打印命中数，防某一分支静默失效 |
| 不变量③ | 询价判定式命中种子询价 99101 → 立即 `exit 4` |
| 条件 UPDATE | 回滚语句带「当前值 = 已知污染值」+「垃圾单据确已清除」双重守卫 |
| 留档 | 删除前打印命中项目完整明细；删除后打印存活项目清单供人工复核 |

两次 execute 均通过备份校验（73 MB / 219 个 CREATE TABLE）。

### 3.2 实际执行结果

**D-0 演示项目下的雪花 ID 垃圾（206 行）**

| 表 | 行数 | deleted 状态 |
|---|---|---|
| biz_payment_apply | 60 | 35 APPROVED(0) + 12 SUBMITTED(0) + 1 REJECTED(0) + 12 DRAFT(1) |
| biz_invoice_apply | 32 | 全部 deleted=1 |
| biz_subcontract_settlement | 27 | 全部 deleted=1 |
| biz_material_inbound | 25 | 全部 deleted=1 |
| biz_project_material_stock | 25 | deleted=0，qty 全 0 |
| biz_labor_payroll | 14 | 全部 deleted=1 |
| biz_labor_contract | 12 | 全部 deleted=1 |
| biz_payment_received | 12 | 全部 deleted=1 |
| biz_purchase_contract | 11 | 全部 deleted=1 |
| biz_construction_contract | 1 | DRAFT deleted=0 |
| biz_purchase_settlement | 1 | APPROVED deleted=0 |

**D-0 污染累计值回滚（4 项）**

取证确证根因：API/E2E 测试脚本拿演示项目当靶子反复提交 1 元单据。

| 字段 | 污染值 | 回滚后 | 差额来源 |
|------|--------|--------|---------|
| `biz_purchase_contract` 91501.cumulative_paid | 6,000,035 | 6,000,000 | 35 条 1 元 APPROVED 付款单 |
| `biz_purchase_contract` 91501.cumulative_settlement | 7,000,001 | 7,000,000 | 1 条 1 元 APPROVED 采购结算 |
| `biz_subcontract` 91801.cumulative_settlement | 3,000,250 | 3,000,000 | 25 条 10 元分包结算（已 deleted=1） |
| `biz_project` 90001.total_expense | 15,000,035 | 15,000,000 | 同 35 条 1 元付款单 |

> 关键认知：污染是「自洽」的——垃圾单据与累计值同步增加，故 91501 在修复前反而
> 显示 MATCH。**只删单据不回滚累计值会制造反向 MISMATCH**，两者必须原子完成。

**D-1 孤儿子表 + 34 个存活测试项目（2894 行）**

- D-1a 引用已逻辑删除项目的孤儿：约 1000 行（`sys_user_project` 372、`biz_project_member` 343、`biz_construction_contract` 244 …）
- D-1b 引用物理不存在项目的孤儿：约 1060 行（`sys_user_project` 628、`sys_budget_control_config` 234、`file_info` 46 …）
- D-1c 34 个存活测试项目及其子表：约 800 行

34 个项目的构成（判定分支命中明细）：

| 分支 | 命中 |
|------|-----|
| `project_name LIKE 'E2E_TEST_%'` | 7 |
| `project_name REGEXP '_1[0-9]{12}'` | 21（含上述 7 个 + 12 个 `E2E审批UI_*` + 2 个 `E2E自动化测试项目_*`） |
| `project_name LIKE 'API测试项目%'` | 11 |
| 显式 ID 列表（见下） | 2 |
| **去重合计** | **34** |

其中 2 个不在原判定式内、经单独取证后由用户批准加入：

| 项目 | 定性依据 |
|------|---------|
| `2089276036854378498`「滨江花园归零演示项目」CLOSED | 名下 19 条付款单中 **11 条为 1 元 APPROVED 劳务付款**，与 90001 同源污染；`total_expense=800,012` vs 实际付款 400,012 |
| `2090675172153552898`「ceshi」DRAFT | 名称即拼音「测试」，空壳项目仅 4 条日志级子记录 |

清理后存活项目 = **精确 4 个演示种子**（90001 滨江花园一期 / 90002 城南市政道路改造 /
90003 高新区产业园二期 / 90004 城北河道综合整治），无误删、无残留真实业务项目。

**D-2 询价家族测试残留（6 行）**

`biz_inquiry` 无 `project_id` 列，不随项目级联，故单列一段。按依赖逆序删除
（`biz_quotation_detail` → `biz_quotation` → `biz_bid_result` → `biz_inquiry_supplier`
→ `biz_inquiry_item` → `biz_inquiry`）：3 条 `E2E_TEST_*_询价`（PUBLISHED）+ 3 条 inquiry_item。

### 3.3 执行过程中发现并修正的自身缺陷

**判定式中文字面量被误插空格（严重，差点漏删 11 个项目）**

编辑脚本时 `'API测试项目%'` 被写成 `'API 测试项目%'`、`'E2E审批UI%'` 被写成 `'E2E 审批 UI%'`，
导致命中项目数从应有的 34 降为 23。`E2E审批UI_*` 因带时间戳后缀仍被 REGEXP 分支兜住，
只有 11 个 `API测试项目-已修改` 静默漏网。

**更危险的是 dry-run 总数完全没有变化**（两次都是 2976）：丢失的 11 个 API 项目的子表行数
恰好与新增的 A+B 子表行数相当（各约 124 行），互相抵消。若只看总数就会误判为「无变化，可以执行」。

发现途径：人工比对命中项目清单，察觉 `API测试项目` 条目全部消失。
修正后追加了不变量①②（种子保护断言 + 逐分支命中明细），使此类漂移今后立即暴露。

### 3.4 未处理项：ACT_RU_TASK 156 条

审计中为 **INFO 级（R5-01）**，非 FAIL/WARN，不阻塞验收。**刻意不在脚本内删除**：

Flowable 的 39 张 `ACT_` 表之间存在外键与引擎内部状态机耦合，直接 `DELETE` 行会让
流程引擎缓存与库状态不一致（下次审批即报错）。正确做法是经应用层
`runtimeService.deleteProcessInstance(procInstId, reason)` 逐实例终止，
由引擎自行级联清理 `ACT_RU_*` 并写 `ACT_HI_*`。

---

## 四、阶段 B：种子单据补齐（R7-03）

迁移：`deploy/db-init/52_V2026_50__seed_reconcile_documents.sql`（328 行）
执行方式：`bash run-migration.sh 52_V2026_50__seed_reconcile_documents.sql`

### 4.1 策略：补单据，不改叙事

以既有 `cumulative_*` 与 `biz_project_settlement` 声明的构成为**事实源**反向补齐支撑单据。
所有金额均由既有种子/结算单推导，**非任意捏造**：

- **90002 支出链** ← 结算单 93301 声明：分包 5M + 劳务 8M + 材料 12M + 机械 3.5M + 其他 1M = 29.5M
- **90004 全链** ← 结算单 93302 声明：分包 2M + 劳务 3M + 材料 2.5M + 机械 0.5M = 8M；收入侧 10M

### 4.2 两处必须「改累计值」的例外

**① 污染回滚**（见 3.2）

**② 91601 / 91801 的 `cumulative_paid` 上调**

种子 `31_V2026_26` L543 注释明写「付款申请（材料600万+劳务400万+分包300万=1300万）」，
即作者本意就是 91601 付 4M、91801 付 3M，而这两份合同的 `cumulative_paid` 却写成 3M / 2M。
**是累计值写错了，不是单据写错了** → 上调累计值以匹配既有单据，而非篡改单据金额。

超付校验（阈值 `contract_amount × 1.05`）：91601 → 4M ≤ 6.3M ✓；91801 → 3M ≤ 5.25M ✓

### 4.3 `total_expense` 口径认定

`SubcontractSettlementService` L225-231 注释给出权威定义：

> totalExpense 统一为「付款口径」（实际现金流出），仅由付款申请审批通过
> （`PaymentApplyService.onApproved`）与资金调拨回写

即 **不含 `biz_other_payment`**（后者单列于 `total_other_payment`）。审计 3.3 的比对口径与此一致。

据此，90002 的 5 张付款单必须合计 29.5M，其中「其他 1M」走
`biz_other_contract` + `contract_category='OTHER_EXPENSE'` 路径
（`PaymentApplyService.addCumulativePaid` 对非 `MODULE_CATEGORIES` 类别一律路由到
`otherContractMapper`）。如此 **无需修改已审批结算单 93301 的声明构成**。

### 4.4 顺带修正的种子缺陷：93311-93313 跨项目引用

`biz_settlement_contract_detail` 的 93311-93313 属于结算单 93301（`project_id=90002`），
`contract_id` 却指向 **90001** 的合同 91501 / 91601 / 91801。

决定性证据：93312 / 93313 的 `contract_code` 分别是 `LW20250601001` / `FB20250701001`
（2025 年，契合 90002 的 2025 工期），而 91601 / 91801 的真实编号是
`LW20260201001` / `FB20260301001`（2026 年）——**编号与 ID 指向的不是同一批合同**。
说明种子作者本意就是给 90002 建自己的合同，只是漏建了。

本迁移补出 90002 的合同后，把这 3 行的 `contract_id` 接回本项目，并补齐 93301 缺失的
机械（3.5M）与其他（1M）两行明细；同时为 93302 补齐原本一行都没有的 4 行明细。

### 4.5 90004 是 E2E 结项夹具，改动安全性已验证

90004 由 `45_V2026_43__seed_closeable_project.sql` 专为 E2E 结项链路而造。
`ProjectService.checkCloseConditions` 的四项预检只读 `biz_project` 字段
+ `biz_project_settlement` 存在性，**不查合同/产值/付款单**。

本迁移不改 90004 的 `status` / `cumulative_output` / `total_income`，
故预检条件②（`|cumulative_output − total_income| ≤ 100` → `|10M − 10M| = 0`）等四项结果均不受影响。

### 4.6 ID 与编号分配

- 全部新行使用 **99500-99999** 段，执行前经探针确认 15 张相关表该段占用数均为 **0**
- 9 个新合同编号经唯一性预检，当前占用数均为 **0**
- 全部 `INSERT` 用 `INSERT IGNORE`，全部 `UPDATE` 带旧值守卫 → **可重复执行**

### 4.7 迁移内置自校验（执行输出实测）

| 校验 | 结果 |
|------|------|
| 7.1 支出合同 `cumulative_paid` 勾稽 | 0 行不匹配 |
| 7.2 `total_expense` | 90001=21M、90002=29.5M、90003=0、90004=8M，diff 全 **0.00** |
| 7.3 `total_income` / `cumulative_output` | 4 项目全部对齐（90002 收入 31M/产值 33.5M；90004 均 10M） |
| 7.4 施工合同开票/收款 | 91001（25M/20M）、91002（32M/31M）、99551（10M/10M）全部对齐 |
| 7.5 结算明细跨项目引用 | 0 行 → 93311-93313 已接回 90002 自己的合同 |

---

## 五、审计脚本自身的 3 处缺陷修正

修复过程中发现审计脚本 `keys/audit-data-round7.sh` 存在 3 处会误导判断的缺陷，一并修正：

### 5.1 机械结算「假 PASS」（严重）

3.2 节原用 `biz_machine_work_settlement.contract_id` 做 JOIN，但**该表根本没有 `contract_id` 列**
（只有 `project_id` + `settlement_code`，按工作量结算）。SQL 报错返回空 → 被误判为
「0/8 MISMATCH PASS」。

改用 `biz_machine_settlement`（`00_schema.sql` L1591-1608 确认有 `contract_id`）后，
**暴露出 1 条真实 MISMATCH**（合同 `2089386615363399682`：累计 3,000 vs 实际 0）。
`biz_machine_work_settlement` 改为单独的 INFO 级核对（10 笔 / 279,000 元），不参与合同级勾稽。

> 碰巧原结论方向正确（机械结算确实存在缺口），但方法是错的——**报错被当成"零违规"**，
> 属于最危险的一类静默失败。

### 5.2 5.4 节漏检 `biz_other_contract`

原检查遍历 labor / machine / subcontract / purchase / construction 五张合同表，
**唯独漏掉 `biz_other_contract`**。而 `PaymentApplyService.addCumulativePaid` 明确会把
非 `MODULE_CATEGORIES` 的类别路由到 `otherContractMapper`，即「其他支出合同」是合法付款标的
（种子 91402 就是带 `cumulative_paid=100000` 的 OTHER_EXPENSE 合同）。
该功能一旦被真实使用即产生**假 FAIL**。

### 5.3 4.5 节「种子项目数」期望值过期

期望值写死 `3`，但种子项目实为 **4** 个——90004 是 `45_V2026_43` 为 E2E 结项链路后补的夹具，
当时未同步上调期望值，导致长期误报 WARN。已改为 `4`。

---

## 六、交付物清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `zw-common/.../event/project/ProjectDeletedEvent.java` | 新增 | 级联契约（置于共享内核避免循环依赖） |
| `zw-project/.../service/ProjectService.java` | 修改 | `delete` 加 `@Transactional` + 两层级联 + 事件发布 |
| `zw-project/.../mapper/BizProjectMemberMapper.java` | 修改 | `logicDeleteByProjectId` |
| `zw-project/.../mapper/BizProjectWbsNodeMapper.java` | 修改 | `logicDeleteByProjectId` |
| `zw-project/.../mapper/SysUserProjectMapper.java` | 修改 | `deleteByProjectId`（物理删，该表无 deleted 列） |
| `{contract,material,machine,labor,finance,purchase,subcontract,budget,site,tender,file}/.../listener/*ProjectCascadeCleanupListener.java` | 新增 ×11 | 各模块级联清理，覆盖 66 张跨模块表 |
| `zw-project/src/test/.../ProjectServiceTest.java` | 修改 | 补 4 个 `@Mock` + 2 个级联用例 |
| `zw-project/src/test/.../ProjectMutationTest.java` | 修改 | 构造函数补至 8 参 |
| `zw-contract/src/test/.../ContractProjectCascadeCleanupListenerTest.java` | 新增 | 样板 Listener 的 3 个用例（正常/异常传播/幂等） |
| `deploy/db-init/52_V2026_50__seed_reconcile_documents.sql` | 新增 | 阶段 B 全量补齐（328 行，含 7 段自校验） |
| `keys/cleanup-garbage-data.sh` | 新增 | 阶段 D 清理脚本（dry-run 默认 + 3 道不变量断言） |
| `keys/audit-data-round7.sh` | 修改 | 3 处缺陷修正（见第五节） |
| `audit-reports/R7-fix-validation-report.md` | 新增 | 本报告 |

---

## 七、复现步骤

```bash
# 1) 代码层验证（本地，无需 Docker）
cd zw-insight-server
mvn -B -o -DskipTests test-compile          # 23 模块 BUILD SUCCESS
mvn -B -o -pl zw-project,zw-contract -am test \
    -Dtest=ProjectServiceTest,ProjectMutationTest,ContractProjectCascadeCleanupListenerTest \
    -DfailIfNoSpecifiedTests=false

# 2) 清理存量（服务器，先 dry-run 再执行）
bash keys/cleanup-garbage-data.sh              # 只统计，零写入
bash keys/cleanup-garbage-data.sh --execute    # 自动 mysqldump 备份 + 校验后删除

# 3) 补齐种子（服务器）
bash deploy/run-migration.sh 52_V2026_50__seed_reconcile_documents.sql

# 4) 验证归零
./keys/audit-data.ps1                          # 期望 PASS=65 FAIL=0 WARN=0
```

**备份文件**（服务器，可用于回滚）：
- `/root/zwi-deploy/backups/zw_insight_pre_cleanup_20260918T025307Z.sql`（D-0/D-1 前，73 MB）
- `/root/zwi-deploy/backups/zw_insight_pre_cleanup_20260918T030743Z.sql`（D-2 前，73 MB）

---

## 八、后续建议

1. **测试脚本必须改用隔离租户**：本轮全部污染的根因是 API/E2E 测试脚本把租户 1 的
   演示项目当靶子反复提交 1 元单据。`keys/lifecycle-sim-v2.sh` 已正确使用 tenant_id=9999，
   但 `keys/test-api-*.sh` 系列仍在打租户 1。建议统一切到 9999，否则污染会复发。
2. **级联删除的集成测试**：本地无 Docker 导致全上下文测试被跳过，建议在 CI 的 L2
   Testcontainers 阶段补一个「建 DRAFT 项目 → 挂满各类子表 → 删除 → 断言 69 表全部清空」
   的端到端用例，真实验证 11 个 Listener 均被触发。
3. **`biz_other_contract` 纳入常规勾稽**：3.1 节目前只勾稽四类支出合同，
   其他支出合同的 `cumulative_paid` 无单据核对，建议补一项。
4. **对象存储孤儿回收**：`FileProjectCascadeCleanupListener` 刻意只清 `file_info` 索引、
   不删 MinIO 物理对象（级联路径中做不可逆存储删除风险过高，且 MinIO 删除无法参与
   数据库事务回滚）。建议补一个独立的对象存储巡检任务回收这些存储孤儿。
