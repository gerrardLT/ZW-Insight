# 三轮改造最终交付状态与续作指南

> 生成日期：2026-10-19 ｜ spec：`.kiro/specs/finance-clarity-terminology-ui/`
> 本文档供新会话冷启动读取：了解已交付什么、还差什么、怎么继续。
> 配套阅读：`docs/术语表.md`（术语基准）、`AGENTS.md` §0（开发约定）、本文件。

---

## 一、已完成并上线双机（16 次部署全绿）

### V2026_113 菜单改名 + 安全检查菜单
- 22 条菜单改名（项目台账/进项发票/竣工结算/成本科目(CBS)/机械租赁合同/点工单…），旧名守卫幂等
- 新增「安全检查」独立菜单 910100（role_code 绑定 + 1203 绑定复制）
- 状态显示名唯一源 `biz-labels.ts`：已立项/投标中/待开工/未中标/在建/停工/已结项（DB 枚举不动）
- check:labels 违例 49→0；大事记事件中文化（GO_TENDER→进入投标）

### V2026_114 分包结算单号
- `settlement_code` 列 + FBJS 编号规则种子 + 存量回填 + Service 生成（编辑不改号）
- 种子 id 段内 MAX+1 + 占用双守卫（首推撞 900010 教训）

### V2026_115 应付台账
- `biz_payable` + `biz_payable_write_off` + `biz_project.payable_amount` 双写不变量
- 五表 BACKFILL 回填（710 段 id）+ PayableService（FIFO 核销/反冲/账龄五桶）+ 10 单测
- 付款在途占用：`SUM(SUBMITTED)` + validatePaymentLimit 扣在途 + 列表两列
- 五类结算挂点：分包 submit/delete、采购 onApproved/delete、劳务 onApproved/delete、机械 onApproved（按明细合同逐条）——事件 `ExpenseSettlementPayableEvent` 同事务消费

### V2026_116 应付账款台账菜单
- 910063 挂支出组，SUPER_ADMIN/FINANCE_STAFF role_code 绑定

### V2026_117 抄送表
- `biz_workflow_cc` + WorkflowCcService（create 幂等/pageByUser/markRead/unreadCount）
- ApprovalController 三端点 + ApprovalService.startProcess 抄送挂钩（variables.ccUserIds）
- 审批中心第四页签「抄送我的」+ ?tab=ccMe 直达

### 前端交付
- **项目一本账面板**（ProjectOnebookPanel）：六条线全渲染（收入/支出/成本/利润三口径/资金/税务），接入项目详情「项目经营」首页签
- **ProjectOnebookMapper**：14 条只读聚合 SQL（跨表 UNION 五类合同/银行流水勾稽现金/税额/占用），OnebookService 利润三口径
- **合同六段链列**：签证/产值/结算/开票/回款/未收（=合同+签证−回款）+ 本页汇总
- **四段支出列**：采购/劳务/机械合同 合同额/累计结算/已付/应付未付 + 本页合计
- **驾驶舱**：三口径说明条 + 落袋利润卡（调 onebook API）
- **首页工作台**：待办/已办/我发起/未读四计数卡 + 审批中心 ?tab= 直达
- **ZwListToolbar**：契约件（刷新+列设置+slot），project/index + payment-apply + payment-received + subcontract/contract + invoice-received + invoice-issued 六页接入
- **useRowNavigation** composable：两页内联键盘导航去重
- **发票 OCR**：tesseract.js v5 前端 WASM 本地免费，invoice-received 三入口
- **登录欢迎 toast**：ElMessage.success 欢迎回来
- **一本账分类色点**：分包蓝/劳务绿/材料橙/机械紫/其他灰
- **router meta.group 七分组**：财务域 收入/支出/发票税务/资金/专项/分析/治理

### 3f 列设置收口（2026-10-19，本地完成待部署）
- 四页列设置从空数组升级为真实可配置：分包合同（7 列）/回款登记（8 列）/进项发票（15 列）/销项发票（11 列），逐列 `v-if="columnVisible[i]"` + `useColumnSetting` 持久化
- 应收账款台账接入 ZwListToolbar（8 列），刷新同时重载台账与账龄；8 样板页换壳至此 8/8 完成
- 进项发票汇总行按列 `property` 匹配，隐藏列不影响本页合计口径
- finance-hub 壳测试与业务页解耦（Tab loader 改用异步桩），修复 teardown 竞态（用例全绿但 exit≠0）
- 验证：vitest 137 文件/1387 通过/0 unhandled（exit 0）；vue-tsc 149 = 改动前基线（0 新增）；check:labels violations=0；stylelint 改动文件 0 告警

### 3g 抄送人 + 发票库认领（2026-10-19，本地完成待部署）
- 抄送：`UserMultiSelector` + `ApprovalSubmitDialog`；付款申请/竣工结算提交弹窗选抄送人（ccUserIds→variables→biz_workflow_cc）；分包结算无 Flowable 流程不接入，通用审批 `/start` 已禁用
- 发票库：发票号唯一，实现为「未关联合同发票认领」（条件 UPDATE 防并发 + 回写累计收票；跨项目拒绝），不复制新建
- 验证：vitest 138 文件/1390 通过+2 skipped（exit 0）；vue-tsc 149 = 基线；stylelint 0 告警；check:labels violations=0

### 3h 支出合同族修订（2026-10-19，本地完成待部署）
- 迁移 V2026_123 双轨：`biz_contract_revision` 增 `contract_category`（默认 CONSTRUCTION，存量回填 + category/contract_id 索引）
- 后端 SPI `ExpenseContractRevisionHandler`：采购/劳务/机械/分包各自实现（版本校验/累计值下限/快照/乐观锁回写含税金额与税额三件套），ContractRevisionService 按类别分派
- 权限：修订接口 OR 放行五类 view 码（四类支出合同现状无细粒度 edit 码）
- 前端：通用 `ExpenseContractRevisionDialog` + 四页 EFFECTIVE 行「修订」入口
- 验证：vitest 139 文件/1393 通过+2 skipped（exit 0）；vue-tsc 149 = 基线；stylelint 0 告警

### 3i ZwFormPane + 3 样板（2026-10-19，本地完成待部署）
- `ZwFormPane` 契约容器：标题/描述、分区标题样式、操作区/审批区插槽、派生只读样式、可选锚点导航（弹窗样板不启用）
- 三样板接入（只改结构不改字段/提交）：付款申请、分包合同、分包结算
- 验证：vitest 140 文件/1397 通过+2 skipped（exit 0）；vue-tsc 149 = 基线；stylelint 0 告警

### 3j 驾驶舱落袋卡 + 账本审计收口（2026-10-19，本地完成待部署）
- `GET /v1/dashboard/cockpit/pocket-profit`（projectId 可选）复用一本账银行勾稽口径，只读不落库；驾驶舱第 9 张独立卡（失败显示 —）
- L4：lifecycle stage_9 追加 9C（分包结算 100000 → 应付生成 → 付款 60000 审批 FIFO 核销至 40000），含应付与项目 payableAmount 硬断言；**未连服务器，待部署后实跑**
- audit-data Section 3.7：`biz_project.payable_amount` vs OPEN 应付余额勾稽（容差 0.01）；应付分页新增 contractId 筛选
- 验证：vitest 140 文件/1397 通过+2 skipped（exit 0）；vue-tsc 149 = 基线；stylelint 0 告警；check:labels violations=0

### 3k 代码审阅与修复（2026-10-19）
- CodeReview：1 Critical + 3 High + 3 Medium + 2 Low；已修 C1（雪花 ID 改字符串）/H1（认领合同校验+回写命中）/H2（9C 独立合同）/M1（行锁）/M2（税额置零）/M3（抄送校验+事务）/L1（移出流程变量）/L2（日志改名）
- **已知越权面待治理**：修订写接口用五类 view 码 OR 放行，需 V2026_124 写权限码迁移 + 角色绑定
- 验证：vitest 140 文件/1397 通过+2 skipped（exit 0）；vue-tsc 149 = 基线；stylelint 0；check:labels violations=0；后端未本地编译

---

## 二、任务状态（P1 已完成，剩余 P2 见下）

### P1 级（已全部完成，2026-10-19）

- 四页列设置、ZwFormPane + 3 样板、支出族修订、抄送人、发票库、驾驶舱落袋卡：交付细节见上方 3f-3j。

### P2 级（后续迭代）

| 项 | 说明 |
|---|---|
| 20 页批量铺设 | 财务域其余+支出合同族，**须逐页确认 useColumnSetting 有无**（3e 回退教训） |
| 项目分类管理 | projectType 自由文本→sys_dict + 表单/筛选/导入统一 |
| 多合同付款申请 | 一次付多合同 |
| 统一待付池 | 跨单据待付汇总+统一去支付 |
| 无关联收付 | 押金/零星/代垫 |
| 借款/还款单 | 独立于备用金 |
| 客户/供应商维度台账 | 行=主体、列=指标 |
| 合同计划明细（付款/收款节点） | 合同含税金额拆为多期计划 |
| sys_menu 二级目录方案 B | 前端 group 不改权限但不够彻底 |
| 项目主数据字段补充 | 监理/设计/勘察/资金来源/立项批文/施工许可证/合同工期/项目等级/建设规模/项目经理/技术负责人 |
| 项目部门树 | 工程部/技术部/商务部/物资部/安全部/财务部 |
| 光伏 EPC Step* 前期开发 | 仅目标客户是光伏 EPC 时 |
| PDF 导出模板覆盖 | print-template 系统已有，补 20 类单据模板 |
| 表单暂存覆盖全站 | 目前仅 6 页有 |

---

## 三、部署与验证状态

| 项 | 状态 |
|---|---|
| CI 全绿 | run 39196159950（最后全绿部署）+ 后续 39203265640（抄送挂钩）|
| Flyway | V2026_113-117 五条双机 success=1 |
| verify-payable.sh | 双机 PASS=4 FAIL=0 |
| 菜单抽查 | 项目台账/施工总承包合同/竣工结算/进项发票/安全检查 双机一致 |
| FBJS 单号 | 主环境 900011，11 条回填；徽颍 900011，4 条回填 |
| init-test-tenant | 已重跑（9999 租户 11 条规则） |
| 本地未推提交 | 3f-3k（含审阅修复），已本地验证，待 push 与部署 |

---

## 四、关键教训（新会话必读）

1. **固定 id 种子 push 前必须对双机真库跑占用探针**（V2026_114 撞 900010 教训）
2. **grep -A 提列清单会溢出表边界**——改用边界感知提取（V2026_115 biz_security_bond.status 教训）
3. **改显示名必须 grep 同名测试断言**（BusinessDetailServiceTest 遗漏教训）
4. **新增构造依赖必须穷举 'new X(' 与 '@InjectMocks+类名' 两类测试形态**（9 用例 NPE 教训）
5. **@Value 字段单测必须 ReflectionTestUtils 显式注入**（dueDate 断言教训）
6. **脚本插样式禁止盲插 </style> 前，必须锚定具体规则边界**（vite Unexpected } 教训）
7. **ZwListToolbar 依赖页面的 useColumnSetting setup**——批量换壳前逐页确认
8. **Import 路径以实际文件为准**（SecurityContextHolder 在 common.config 非 common.security）
9. **CRLF/LF 混存**——Python 替换前必须检测行尾并自适应
10. **本机代理 7897 不稳定**——push 失败时直连/代理交替重试
11. **壳测试必须与真实业务页解耦**——HubPage 壳测试加载真实页面会发真实 API 请求，且模块图可能在环境拆除后仍解析（EnvironmentTeardownError，用例全绿但 exit≠0）；Tab loader 一律用异步桩，页面内容由各自单测覆盖
12. **发票号唯一时「从发票库选择」不能复制新建**——唯一键会让重复登记直接 409；正确实现是认领（条件 UPDATE 补合同归属 + 回写累计收票）
13. **抄送只适用于有 Flowable 流程的单据**——分包结算提交即 APPROVED（无流程/无 processInstanceId），不能接抄送；接入前先确认 Service 是否调用 startProcess
14. **不同合同表的主键 id 可重复**——统一修订表必须带 contract_category 并在列表/在途/详情全部按类别过滤，否则施工合同与支出合同会串号
15. **跨模块能力用 common SPI + 各模块实现**——四类支出合同修订放入 zw-contract 会形成循环依赖；接口放 common、实现放各模块、Service 注入 List 分派
16. **雪花 ID 禁止 Number() 转换**——Jackson 全局把 Long 序列化为 String 就是为了规避 2^53 精度丢失；前端必须字符串原样透传（抄送/提交/认领均适用）
17. **抄送失败不得静默**——抄送与流程启动同事务，无效/停用/跨租户抄送人必须报错回滚；`ccUserIds` 不得写入 Flowable 流程变量

---

## 五、核心文件索引

| 文件 | 用途 |
|---|---|
| `docs/术语表.md` | 术语唯一基准（菜单名/状态名/金额口径/禁用词） |
| `zw-insight-web/src/constants/biz-labels.ts` | 状态标签唯一源（DB 枚举→显示名+徽章色） |
| `zw-insight-web/scripts/check-labels.mjs` | 术语审计（npm run check:labels） |
| `zw-insight-web/src/components/ZwListToolbar.vue` | 列表工具栏契约件 |
| `zw-insight-web/src/composables/useRowNavigation.ts` | 键盘行导航 |
| `zw-insight-web/src/utils/invoice-ocr.ts` | 发票 OCR（tesseract.js） |
| `zw-insight-server/.../ProjectOnebookService.java` | 一本账聚合 |
| `zw-insight-server/.../PayableService.java` | 应付台账 |
| `zw-insight-server/.../WorkflowCcService.java` | 审批抄送 |
| `keys/verify-payable.sh` | 应付勾稽核验（双机 PASS=4） |
| `.kiro/specs/finance-clarity-terminology-ui/tasks.md` | 任务台账（含受阻项/教训/部署记录） |
