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

---

## 二、未完成项（按优先级）

### P1 级（1-2 个迭代）

| 项 | 现状 | 续作指引 |
|---|---|---|
| **四页列设置升级** | 传空数组，ColumnSettingPopover 隐藏 | 逐页定义 columns 数组→补 useColumnSetting→v-if 绑定→传真实 columns |
| **ZwFormPane + 3 样板** | 组件未建 | 分区标题+锚点导航+暂存/提交+审批区+派生只读样式；样板：付款申请/分包合同/分包结算 |
| **支出族修订** | 施工合同已有修订按钮(V2026_106)；四类支出合同无 | 后端加 revise 端点 + 前端修订按钮 |
| **抄送人表单选择器** | 表+端点+页签已建，但无表单能选抄送人 | 付款申请/竣工结算/通用审批的表单区加选择器，提交时 variables.ccUserIds=[...] |
| **进项发票「从发票库选择」** | OCR+手工已有；缺引用已有发票 | 弹窗列已有发票→选择→填入 |
| **驾驶舱完整落袋卡片** | 当前是说明条指向项目详情 | 从说明条升级为第 9 张独立卡片 |

### P2 级（后续迭代）

| 项 | 说明 |
|---|---|
| 20 页批量铺设 | 财务域其余+支出合同族，**须逐页确认 useColumnSetting 有无**（3e 回退教训） |
| L4 lifecycle-sim-v2 应付断言 | verify-payable.sh 已建双机 PASS=4，须纳入 lifecycle 脚本 |
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
| audit-data 勾稽增补 | biz_project.payable_amount = biz_payable OPEN 余额合计 检查项 |

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
| 本地未推提交 | 无（全部已推送） |

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
