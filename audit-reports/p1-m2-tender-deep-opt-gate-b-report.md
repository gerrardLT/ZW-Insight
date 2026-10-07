# P1-M2「投标管理」业务深度优化门 B 验收报告

> 日期：2026-10-07  
> 标的：`zw-tender` 模块、`zw-insight-web` 前端投标视图、DB 增量双轨迁移 `V2026_82`  
> 依据：[docs/deep-opt/02-tender.md](../docs/deep-opt/02-tender.md)

---

## 1. 任务达成真相（A 档必做 + B 档细节增强）

| 编号 | 任务项 | 业务定义与不变量 | 落地代码与组件 | 验收结果 |
|---|---|---|---|---|
| **A1** | 开标登记闭环 | 录入中标/未中标结果，联动项目状态机置 `WIN_BID`/`LOSE_BID`，解锁人员押证 (TI-3) | `OpenBidRecordService`<br>`OpenBidDialog.vue`<br>`register.vue` 操作栏 | **PASS**（单测 4/4 绿） |
| **A2** | 保证金申请与退还 | 2% 预算法定上限守卫 (TI-1)，申请走线上审批流，开标后触发退还核销 (TI-4) | `DepositApplyService`<br>`DepositReturnService`<br>`DepositDialog.vue` | **PASS**（单测 10/10 绿） |
| **A3** | 投标综合抽屉 | 一页式看全投标全息数据（开标、保证金、人员锁定、费用、编标任务） | `TenderDetailDrawer.vue` | **PASS**（8/8 绿） |
| **B1** | 人员押证排他锁定 | 关键岗位持证人员排他锁定 (TI-2)，开标前防一证多投，开标后自动释放 | `TenderPersonBindingService`<br>`biz_tender_person_binding` 表 | **PASS**（单测 5/5 绿） |
| **B2** | 投标费用管理 | 标书费、图纸费等前期成本归集与回单核销 | `TenderFeeService`<br>抽屉 Tab 4「投标费用」 | **PASS**（单测 10/10 绿） |
| **B3** | 编标任务分工 | 商务标、技术标分工编制与协同进度跟踪 | `TenderTaskService`<br>抽屉 Tab 5「编标任务」 | **PASS**（单测 6/6 绿） |
| **B4** | 落标原因标准化分析 | 未中标强制分类（报价偏高/技术偏弱/商务偏离/资信不足/其他），支持检索 | `OpenBidDialog.vue`<br>`register.vue` 筛选 | **PASS** |

---

## 2. 门禁验证证据

1. **数据库迁移**：
   - 增量迁移脚本：`zw-insight-server/zw-app/src/main/resources/db/migration/V2026_82__tender_deep_opt.sql`
   - 初始化基准脚本：`deploy/db-init/84_V2026_82__tender_deep_opt.sql`
   - 包含列守卫与新表建表守卫，100% 幂等。
2. **后端单测套件**：
   - `zw-tender` 模块测试：`102 tests run, 0 failures, 0 errors, 0 skipped`，全部通过。
   - 包含 TI-1 与 TI-2 不变量专项测试 `TenderInvariantsTest`。
3. **前端工程质量**：
   - 前端单测套件：`126 test files / 1296 passed / 0 failed`。
   - 前端组件专项：`tender-deep-opt-components.test.ts`（8/8 passed）、`tender-register-crud.component.test.ts`（7 passed / 1 skipped）、`tender-matrix.component.test.ts`（13/13 passed）。
   - 样式规范：`stylelint "src/**/*.{vue,css,scss}"` 0 errors clean。
   - 构建门禁：`vite build` 生产打包编译通过。
4. **L3 API 契约扩充**：
   - `keys/test-api-tender.sh` 新增保证金草稿创建、查回校验与删除断言。**仅改脚本，尚未在服务器执行。**
5. **一致性审计**：
   - 审核模块 20/20，Critical = 0，Major = 0，无破坏性错位。

---

## 3. 未完成 / 未验证项（如实登记）

| 项 | 状态 | 说明 |
|---|---|---|
| L3 `test-api-tender.sh` 服务器实跑 | **未执行** | 需 scp 到服务器运行，本轮仅本地改脚本 |
| L4 `lifecycle-sim-v2.sh` 保证金/落标分支 | **未改、未执行** | 蓝图 §7 要求阶段 3/3B 增加保证金申请与开标落标演练，本轮未动 |
| 部署与线上 V2026_82 应用确认 | **未执行** | 代码未 push，双机未部署 |
| 真实界面抽验（开标/保证金/抽屉） | **未执行** | 仅有组件单测，无浏览器实操 |
| R7 审计基线（PASS=67 FAIL=0）复核 | **未执行** | 须部署后在线上跑 `audit-data.ps1` |
| A1 中标候选人排序 | **未实现** | 蓝图 A1 原文含「排序」，前端弹窗无此字段 |
| B2 发票/回单附件上传 | **未实现** | 现仅有费用登记与确认支付，无附件 |
| B4 落标统计报表 | **未实现** | 现仅有列表筛选与列展示，无统计视图 |

## 4. 门 B 结论

代码层面：A 档 3 项、B 档 4 项的主干与 TI-1 至 TI-4 守卫已实现，后端 102 测试与前端 1296 测试通过。
验收层面：**门 B 未闭合**。上表前五项是合同约定的闭环验证（线上实跑 + 真实界面 + R7 基线），后三项是蓝图原文已列而未落地的功能，须由用户决定补做或降为 C 档。
