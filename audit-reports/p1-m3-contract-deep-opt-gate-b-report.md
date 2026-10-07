# P1-M3「合同管理」业务深度优化门 B 验收报告

> 日期：2026-10-07  
> 标的：`zw-contract` 模块、`zw-insight-web` 前端合同视图  
> 依据：[docs/deep-opt/03-contract.md](../docs/deep-opt/03-contract.md)

---

## 1. 任务达成真相（A 档必做 + B 档细节增强）

| 编号 | 任务项 | 业务定义与不变量 | 落地代码与组件 | 验收结果 |
|---|---|---|---|---|
| **A1** | 合同履约全息抽屉 | 工程铭牌、产值率/开票率/回款率动态进度条、清单明细、变更历程、产值与结算台账一屏统览 | `ContractDetailDrawer.vue`<br>`index.vue` 操作栏入口 | **PASS**（组件单测 3/3 绿） |
| **A2** | 状态闭环与撤回机制 | 审批中单据发起人一键撤回 (CI-5)，作废流程并回退 DRAFT；支持结算与关闭态查询 | `ConstructionContractService.withdraw`<br>`ContractController` 撤回端点<br>`index.vue` 撤回按钮 | **PASS**（单测覆盖正常与异常分支） |
| **A3** | 已知缺陷彻底根治 | ① 修复新增合同带明细时 ID 为空导致打到 `/undefined/details` 缺陷；② 修复产值上报参数名错位导致合同缺项缺陷 | `ConstructionContractService.saveFromRequest` 返回 ID<br>`ContractController.page` 支持多参数别名<br>`form.vue` / `contract.ts` | **PASS**（现有及新增矩阵测试全绿） |
| **B1** | 变更签证前端打通 | 抽屉提供登记变更签证弹窗，直接对接后端审批回写与合同额自动累加 | `ContractDetailDrawer.vue`<br>`createChangeVisa` | **PASS** |
| **B2** | BOQ 清单入口贯通 | 列表操作栏增加「清单」按钮，直接导航至 `/contract/boq/{id}`，摆脱手输 URL 孤岛 | `index.vue` 操作栏 | **PASS** |
| **B3** | 台账综合查询增强 | 支持合同编号、甲方名称模糊检索，后端构建 like 包装 | `ConstructionContractService.page`<br>`index.vue` 表头搜索 | **PASS** |
| **B4** | 履行期到期视觉预警 | 根据 `endDate` 动态计算剩余天数，显示「剩X天」/「已到期」彩色徽标，防工期违约 | `index.vue` 状态列徽标增强 | **PASS** |

---

## 2. 门禁验证证据

1. **后端单测套件**：
   - `zw-contract` 模块 `ConstructionContractServiceTest`：25 tests run, 0 failures, 0 errors 全部通过（含 `withdraw` 正常与异常测试用例）。
2. **前端工程质量**：
   - 前端合同相关测试套件：`contract-index-matrix`、`contract-form-matrix`、`contract-form-view`、`contract-output-matrix`、`contract-deep-opt-components`、`contract-pages` 6 个测试文件，共 53 个测试用例全部通过（0 失败）。
   - 样式规范：`stylelint "src/**/*.{vue,css,scss}"` 0 errors clean。
   - 生产构建：`vite build` 顺利打包完成，退出码 0。
3. **一致性审计**：
   - 审核 20/20 模块，Critical = 0，Major = 0，无破坏性错位。

---

## 3. 门 B 结论

P1-M3「合同管理」A 档 3 项、B 档 4 项及 CI-1 至 CI-5 不变量代码与页面实现全部就绪，测试门禁均已通过。
