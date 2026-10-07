# P3-M5「采购与材料」业务深度优化门 B 验收报告

> 日期：2026-10-08  
> 标的：`zw-purchase` 模块、`zw-material` 模块、`zw-budget`（CBS 口径归一）、`zw-insight-web` 前端、`zw-insight-app` 移动端  
> 依据：[docs/deep-opt/05-purchase-material.md](../docs/deep-opt/05-purchase-material.md)（v1.1 复审修订版）

---

## 1. 任务达成真相（A 档 4 项 + B 档 6 项全量落地）

| 编号 | 任务项 | 业务定义与不变量 | 落地实现 | 验收结果 |
|---|---|---|---|---|
| **A1** | 采购合同/结算真审批 | 提交置 `SUBMITTED`；新增 `PurchaseApprovalListener` 监听通过置 `EFFECTIVE`/`APPROVED` 并原子累加累计结算；驳回置 `DRAFT`（PI-3） | `PurchaseContractService`<br>`PurchaseSettlementService`<br>`PurchaseApprovalListener` | **PASS**（单测覆盖通过/幂等/驳回全分支，L4 阶段 6A/9C 真实跑通） |
| **A2** | 材料库存原子化三修 | 入/出/调/盘四处库存变更改走原子 SQL（`deductStock`/`revertOutbound`/`addInbound`/`revertInbound`/`transferOut`/`transferIn`/`setInventoryQuantity`）；扣减带 `WHERE stock >= qty` 下限守卫；入库删除回冲去 `.max(0)` 钳位改报错（PI-1）；扣减时点维持 `save`（预留语义）不迁移 | `BizProjectMaterialStockMapper`<br>`MaterialInboundService`<br>`MaterialOutboundService`<br>`MaterialTransferService`<br>`MaterialInventoryService` | **PASS**（并发下限测试全绿） |
| **A3** | 字段防篡改 | 合同、入库、出库、调拨、盘点 5 类单据 `update()` 改白名单拷贝，服务端拒绝覆写 `status` 与累计金额（PI-5） | 各单据 Service.update() | **PASS**（单测覆盖防篡改分支） |
| **A4** | CBS 成本口径归一（结算） | `CostRollUpService` 移除材料出库消耗双计项，材料实际成本完全对齐已审批采购结算（PI-4 / AGENTS.md 权威口径）；入库明细金额精度归一 `setScale(2, HALF_UP)`；清理死 stub 与死 mapper 方法 | `CostRollUpService`<br>`CostRollUpMapper`<br>`MaterialInboundService` | **PASS**（线上 R7 Section 3.6 CBS 勾稽无劣化） |
| **B1** | 材料盘点 Web 页面 | 新建 `/material/inventory` 页面（列表+明细+从项目库存载入+保存草稿+确认生效覆写库存），激活后端 5 个死 API；详情接口回填明细 | `views/material/inventory.vue`<br>`api/material.ts`<br>`router/index.ts` | **PASS**（前端组件测试 3/3 绿） |
| **B2** | 状态真实化展示 | 采购合同、采购结算列表展示 `SUBMITTED` 审批中状态；材料入库、出库支持真实状态流转 | `views/purchase/contract.vue`<br>`views/purchase/settlement.vue` | **PASS** |
| **B3** | 结算→付款真实联动 | 采购结算行操作支持「发起付款」，跳转至 `/finance/payment-apply` 并通过 `route.query` 自动预填项目、采购合同与结算金额 | `views/purchase/settlement.vue`<br>`views/finance/payment-apply.vue` | **PASS**（前端测试 52/52 绿，无 unhandled error） |
| **B4** | 询价定标闭环 | `BidRankingService.confirmWinner` 完整回填 `winnerName`、`winnerAmount`、`awardDate`，使中标公示页不再空白 | `BidRankingService` | **PASS** |
| **B5** | 门户安全加固 | 公开报价提交与查询本人报价强制短信验证码校验（接入 `SupplierSmsService`），防未授权探测；接口参数对齐 | `PublicQuotationService`<br>`PublicQuotationController` | **PASS**（单元测试覆盖有效/无效验证码） |
| **B6** | 移动端 submit 链贯通 | 移动端入库、出库、退货通过后端 `autoSubmit=true` 复合端点（单事务），在保存后自动触发 `submit` 使单据与库存真正生效，杜绝移动端只建草稿死单 | `InboundController`<br>`OutboundController`<br>`zw-insight-app` | **PASS**（移动端 30 文件 246 tests 全绿） |

---

## 2. 审阅轮关键发现与纠偏（诚实记录）

| # | 审阅发现 | 处置 |
|---|---|---|
| 1 | `submitOrQueue` 离线提交助手只返回 `{ queued }`，无返回单据 ID；原 B6 尝试从返回值读 ID 导致自动 submit 永不触发 | **改用后端复合端点**：`InboundController`/`OutboundController` POST 接收 `autoSubmit=true`，Service 层单事务原子完成保存与提交 |
| 2 | 新盘点页前端发送 `details[]`，而后端 `save` 契约为 `adjustments: {stockId: 实盘数}`，导致原实现会落空盘点单 | **重构页面与接口**：页面提供「从项目库存载入」自动带出 `stockId` 与账面数，提交时组装 `adjustments` 契约；详情接口回填明细；DRAFT 编辑仅改日期（明细为不可变盘点快照） |
| 3 | `CostRollUpServiceTest` 残留已移除方法的死 stub | **清理干净**：删除死 stub、死 mapper 方法与未用常量 |
| 4 | CRLF / LF 行尾混存导致 diff 假爆炸（3233+/3030- 假变更，实际仅 385 行） | **一键修复**：按 HEAD 主导行尾规范还原，diff 降至纯语义变更，存入项目记忆 `zwi-crlf-diff-noise` |
| 5 | `E2eTestGuard` 创建者门槛经评估对既有测试有负收益 | **整体验收回退**：恢复原实现，SUPER_ADMIN 角色门槛如实记录为后续鉴权链改造项 |

---

## 3. 门禁与线上验证证据

1. **CI 双机自动部署**：
   - GitHub Actions run `37671461086`（commit `ec605ac`）：Backend Build、四端前端测试全部成功；
   - 自动部署矩阵：`主服务器1 (中维 129.204.3.200)` 与 `新服务器2 (徽颍 43.142.44.145)` **双机全部部署成功**；
   - 双机容器状态：`zwi-backend`、`zwi-frontend` 全部 Up 健康。
2. **L3 接口契约实测**（129 服务器现场执行）：
   - `test-api-purchase.sh`：**PASSED**（合同、结算、询价全绿）；
   - `test-api-material.sh`：**PASSED**（入库、出库、调拨全绿）。
3. **L4 全生命周期模拟测试**（租户 9999）：
   - `lifecycle-sim-v2.sh`：**26/26 阶段全 PASSED**（含采购合同、材料入库出库、采购结算、付款闭环）；
   - `verify-l4-clean.sh`：**四项断言全部通过**（biz_ 表 9999 残留总行数 = 0，Flowable 运行时任务残留 = 0，测试基建正常保留，租户 1 零污染）。
4. **R7 生产数据一致性审计**（129 服务器现场执行）：
   - 结果：**PASS=67 FAIL=0 WARN=0 INFO=39**，基线维持满分不劣化（报告 `audit-round7-2026-10-07T19-15-51Z.md` 已回拉入库）。
5. **前端工程质量**：
   - PC 端 Vitest：**129 test files / 1305 passed / 0 failed / 0 unhandled errors**；
   - 移动端 Vitest：**30 test files / 246 passed / 0 failed**；
   - PC 端 Stylelint：**100% clean 0 errors**；
   - PC 端 Vite build：生产打包成功。

---

## 4. 门 B 结论

P3-M5「采购与材料」A 档 4 项、B 档 6 项及 PI-1 至 PI-5 业务不变量全部落地并经代码审阅、CI 构建、双机部署与线上 L3/L4/R7 全链闭环验证。**P3-M5 门 B 达成，正式闭合。**
