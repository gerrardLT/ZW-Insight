# 设计（财务清晰化 / 工程术语 / 前端契约）

## 1. 总体分层

```text
术语层   docs/术语表.md（人读） + src/constants/biz-labels.ts（机器读，唯一枚举源）
菜单层   sys_menu.menu_name 改名（V2026_113） + router meta.title 同值 + /finance meta.group 七分组
呈现层   ZwListPage / ZwFormPane / ZwDetailDrawer 三契约组件 + 8→20→77 页分批换壳
数据层   合同六段列/支出四段列（实体字段已有，纯展示） + 分包结算单号(V2026_114)
服务层   PayableService 对称 ReceivableService（V2026_115/116） + 在途占用拦截
聚合层   ProjectOnebookService 只读端点（阶段三，不落库）
```

## 2. 关键设计决策

| # | 决策 | 理由 |
|---|---|---|
| D1 | 状态只改显示层，DB 枚举不动 | 避免数据迁移/状态机/e2e 断言全链路改；显示名收敛于 biz-labels.ts |
| D2 | 菜单改名只 UPDATE menu_name，不动 id/path | 授权按叶子路径解析（navigation.ts），动 path 会整体失效；无需重绑角色 |
| D3 | 财务域分组用 route `meta.group`，navigation.ts section 优先取之 | 纯前端改动，零权限迁移（方案 A）；方案 B（sys_menu 二级 DIR）留阶段四 |
| D4 | 菜单角色绑定一律 `sys_role.role_code` 查 id | 双机角色模型分叉（主环境 5 角色 vs 徽颍 10 角色），硬编码 id 会漏绑（V2026_54/112 教训） |
| D5 | ZwListPage 是包装容器不是表格替换 | 内置 el-table + 五件套；业务列仍由页面以 slot/columns 传入，避免一次性重写 126 页 |
| D6 | 应付台账对称复刻应收（ReceivableService 为模板） | 模式已被应收验证：幂等生成/FIFO 核销/账龄/项目双写不变量 |
| D7 | onebook 端点只读聚合、不落库不加快照 | R7"累计值必须有单据支撑"；落袋利润是新读法不是新回写 |
| D8 | 换壳不改 api/*.ts 路径 | 一致性审计 Critical=0 基线保持 |

## 3. 迁移版本预算

| 版本 | db-init | 内容 |
|---|---|---|
| V2026_113 | 116_ | 菜单改名（约 23 条 UPDATE，带旧名守卫幂等） |
| V2026_114 | 117_ | biz_subcontract_settlement.settlement_code + serial_number_rule 种子(SUBCONTRACT_SETTLEMENT) + 存量回填 |
| V2026_115 | 118_ | biz_payable + biz_payable_write_off + biz_project.payable_amount |
| V2026_116 | 119_ | 应付台账菜单 + role_code 绑定 |

## 4. 阶段一三线并行与契约边界

- **A 线（术语菜单）**：V2026_113 双轨、router meta.title、/finance meta.group、navigation.ts、biz-labels.ts 接入各页、e2e/单测文案基线同步。
- **B 线（列表契约）**：ZwListPage、useRowNavigation、8 样板页换壳（施工合同/付款申请/回款登记/应收台账/项目台账/分包合同/进项发票/销项发票）。
- **C 线（钱链+单号+工作台）**：合同六段列、支出合同四段列、V2026_114 分包单号（后端+前端）、首页工作台行。
- 冲突面：A 与 B 共改 8 样板页的 statusMap（A 先落 biz-labels，B 换壳时直接引用）；B 与 C 共改合同/分包列表（B 换壳提供容器，C 在容器内加列）。编排：A 先行半天落契约文件，B/C 再并行。

## 5. 验收设计

- 脚本：check-menu-coverage（阻断，须 exit 0）、check-labels（warn，新口径违例=0）、check-test-sync --last（无 UNSYNCED）。
- 前端：vitest 全绿、vue-tsc 改动文件 0 error。
- 后端（阶段二）：CI mvn（禁本地）、PayableService 单测（正常/异常/幂等/并发）、L4 9999 含应付核销断言、R7 只读复核（先辨"新生效 vs 新失败"）。
- 互证：合同六段列数字 ↔ 竣工结算单详情同项目对账（容差 0.01）。
