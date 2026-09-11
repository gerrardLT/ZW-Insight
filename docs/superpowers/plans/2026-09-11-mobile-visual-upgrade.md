# 移动端全量视觉升级 (Industrial Precision Mobile) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将移动端 `zw-insight-app` 全量 38 个业务页面升级为工业精密美学（直角纪律 2px、安全橙 #ff6b00、名牌大写眉题、等宽 tabular-nums），封装轻量微组件并保证现有 170 项 Vitest 单测 100% 通过且 H5 打包成功。

**Architecture:** 分层推进。首先扩展全局 `signature.css` 并实现移动端 `ZwMoney` 与 `ZwStatusBadge` 微组件；接着按四大业务集群（核心枢纽、现场施工履约、物资仓储、财务与成本）分批重塑各页面结构与样式；每批均执行单测回归并最终进行全量打包。

**Tech Stack:** Uni-app (Vue 3 + TypeScript), Vite, Vitest, CSS Custom Properties (Tokens).

---

### Task 1: 扩展基础签名样式与封装移动端微组件

**Files:**
- Modify: `zw-insight-app/src/styles/signature.css`
- Create: `zw-insight-app/src/components/ZwMoney.vue`
- Create: `zw-insight-app/src/components/ZwStatusBadge.vue`
- Test: `zw-insight-app/tests/components.test.ts`

- [ ] **Step 1: 编写微组件单测**

在 `zw-insight-app/tests/components.test.ts` 编写挂载测试：
```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ZwMoney from '../src/components/ZwMoney.vue'
import ZwStatusBadge from '../src/components/ZwStatusBadge.vue'

describe('Mobile Micro Components', () => {
  it('ZwMoney renders tabular formatted amount', () => {
    const wrapper = mount(ZwMoney, { props: { value: 12345.67 } })
    expect(wrapper.text()).toContain('12,345.67')
    expect(wrapper.classes()).toContain('zw-money')
  })

  it('ZwStatusBadge renders status text and badge class', () => {
    const wrapper = mount(ZwStatusBadge, { props: { status: 'success', text: '已通过' } })
    expect(wrapper.text()).toBe('已通过')
    expect(wrapper.classes()).toContain('status-badge-success')
  })
})
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd zw-insight-app && npx vitest run tests/components.test.ts`
Expected: FAIL (Cannot find module)

- [ ] **Step 3: 扩充 signature.css 与实现微组件**

扩展 `zw-insight-app/src/styles/signature.css`：
添加 `.plate-header`、`.page-eyebrow`、`.zw-input`、`.zw-form-group`、`.tabular-num`、`.zw-list-card`。

创建 `zw-insight-app/src/components/ZwMoney.vue` 与 `ZwStatusBadge.vue`。

- [ ] **Step 4: 运行组件单测与基础回归**

Run: `cd zw-insight-app && npx vitest run tests/components.test.ts`
Expected: PASS

- [ ] **Step 5: 提交基座改造**

```bash
git add zw-insight-app/src/styles/signature.css zw-insight-app/src/components/ zw-insight-app/tests/components.test.ts
git commit -m "feat(mobile): 扩展工业签名工具类并封装移动端微组件"
```

---

### Task 2: 集群 1·核心枢纽改造（首页、工作台、审批流、个人中心）

**Files:**
- Modify: `zw-insight-app/src/pages/home/index.vue`
- Modify: `zw-insight-app/src/pages/workbench/index.vue`
- Modify: `zw-insight-app/src/pages/approval/index.vue`
- Modify: `zw-insight-app/src/pages/approval/detail.vue`
- Modify: `zw-insight-app/src/pages/mine/index.vue`
- Modify: `zw-insight-app/src/pages/mine/sign.vue`
- Modify: `zw-insight-app/src/pages/mine/password.vue`
- Modify: `zw-insight-app/src/pages/mine/shortcut-edit.vue`
- Modify: `zw-insight-app/src/pages/message-center/index.vue`
- Test: `zw-insight-app/tests/pages/home-pages.test.ts`
- Test: `zw-insight-app/tests/pages/approval-material-pages.test.ts`
- Test: `zw-insight-app/tests/pages/mine-pages.test.ts`

- [ ] **Step 1: 改造核心枢纽页面样式与结构**
- 首页：顶部统计卡统一直角蓝图角标、等宽字体；快捷功能方块直角化。
- 工作台：项目看板卡片等宽化、直角化；现场常用业务网格更新为精密方块。
- 审批中心：Tab 栏转为工业下划线激活形态，批量审批 Bar 44px 直角，卡片增加状态竖标。
- 个人中心：石墨黑头部名牌化、精密菜单直角收敛。

- [ ] **Step 2: 运行集群 1 单测验证**

Run: `cd zw-insight-app && npx vitest run tests/pages/home-pages.test.ts tests/pages/approval-material-pages.test.ts tests/pages/mine-pages.test.ts`
Expected: PASS 全部测试

- [ ] **Step 3: 提交集群 1 变更**

```bash
git add zw-insight-app/src/pages/home/ zw-insight-app/src/pages/workbench/ zw-insight-app/src/pages/approval/ zw-insight-app/src/pages/mine/ zw-insight-app/src/pages/message-center/
git commit -m "style(mobile): 升级核心枢纽四大 Tab 与账户中心为工业精密风格"
```

---

### Task 3: 集群 2·现场施工与履约业务改造

**Files:**
- Modify: `zw-insight-app/src/pages/machine/work-log/index.vue`
- Modify: `zw-insight-app/src/pages/machine/work-log/create.vue`
- Modify: `zw-insight-app/src/pages/labor/work-order/index.vue`
- Modify: `zw-insight-app/src/pages/labor/work-order/create.vue`
- Modify: `zw-insight-app/src/pages/contract/change-event/index.vue`
- Modify: `zw-insight-app/src/pages/contract/change-event/create.vue`
- Modify: `zw-insight-app/src/pages/contract/change-event/detail.vue`
- Modify: `zw-insight-app/src/pages/site/construction-log.vue`
- Modify: `zw-insight-app/src/pages/site/progress-feedback.vue`
- Modify: `zw-insight-app/src/pages/site/quality-check.vue`
- Modify: `zw-insight-app/src/pages/site/safety-check.vue`
- Modify: `zw-insight-app/src/pages/site/inspection-detail.vue`
- Modify: `zw-insight-app/src/pages/site/watermark-camera/index.vue`
- Test: `zw-insight-app/tests/pages/machine-work-log.test.ts`
- Test: `zw-insight-app/tests/pages/labor-work-order.test.ts`
- Test: `zw-insight-app/tests/pages/site-pages.test.ts`
- Test: `zw-insight-app/tests/pages/watermark-camera.test.ts`

- [ ] **Step 1: 改造现场施工履约页面**
- 机械台班与劳务点工：列表卡片等宽数据排版、填报表单 44px 工业直角控件。
- 变更事件：增补名牌与等宽金额，状态胶囊化。
- 现场质安与检查：整改卡片与隐患警示叠加 hazard 条纹装饰。
- 水印相机：增强刻度与蓝图坐标感。

- [ ] **Step 2: 运行集群 2 业务单测**

Run: `cd zw-insight-app && npx vitest run tests/pages/machine-work-log.test.ts tests/pages/labor-work-order.test.ts tests/pages/site-pages.test.ts tests/pages/watermark-camera.test.ts`
Expected: PASS 全部测试

- [ ] **Step 3: 提交集群 2 变更**

```bash
git add zw-insight-app/src/pages/machine/ zw-insight-app/src/pages/labor/ zw-insight-app/src/pages/contract/change-event/ zw-insight-app/src/pages/site/
git commit -m "style(mobile): 升级机械、劳务、变更与现场质安为工业精密风格"
```

---

### Task 4: 集群 3·物资仓储与集群 4·财务、成本与档案改造

**Files:**
- Modify: `zw-insight-app/src/pages/material/inbound.vue`
- Modify: `zw-insight-app/src/pages/material/outbound.vue`
- Modify: `zw-insight-app/src/pages/material/return.vue`
- Modify: `zw-insight-app/src/pages/project/cost-control/index.vue`
- Modify: `zw-insight-app/src/pages/project/archive.vue`
- Modify: `zw-insight-app/src/pages/finance/invoice-apply.vue`
- Modify: `zw-insight-app/src/pages/finance/invoice-received.vue`
- Modify: `zw-insight-app/src/pages/finance/payment-apply.vue`
- Modify: `zw-insight-app/src/pages/finance/payment-received.vue`
- Modify: `zw-insight-app/src/pages/finance/other-payment.vue`
- Modify: `zw-insight-app/src/pages/finance/reimbursement.vue`
- Modify: `zw-insight-app/src/pages/finance/personal-reimbursement.vue`
- Modify: `zw-insight-app/src/pages/finance/reserve-fund-apply.vue`
- Modify: `zw-insight-app/src/pages/finance/reserve-fund-return.vue`
- Modify: `zw-insight-app/src/pages/login/index.vue`
- Modify: `zw-insight-app/src/pages/login/forgot-password.vue`
- Test: `zw-insight-app/tests/pages/finance-pages.test.ts`
- Test: `zw-insight-app/tests/pages/project-cost-control.test.ts`
- Test: `zw-insight-app/tests/pages/login-page.test.ts`

- [ ] **Step 1: 改造仓储、财务表单与登录页**
- 材料仓储：物料清单设备铭牌排版、加减器与扫码直角化。
- 财务与成本：成本看板三色预警等宽排版、开票/付款/报销表单直角化与金额等宽对齐。
- 登录画布：强化石墨控制室画布与安全橙品牌名牌。

- [ ] **Step 2: 运行集群 3 & 4 业务单测**

Run: `cd zw-insight-app && npx vitest run tests/pages/finance-pages.test.ts tests/pages/project-cost-control.test.ts tests/pages/login-page.test.ts`
Expected: PASS 全部测试

- [ ] **Step 3: 提交集群 3 & 4 变更**

```bash
git add zw-insight-app/src/pages/material/ zw-insight-app/src/pages/project/ zw-insight-app/src/pages/finance/ zw-insight-app/src/pages/login/
git commit -m "style(mobile): 升级材料仓储、财务款项与成本看板为工业精密风格"
```

---

### Task 5: 全量回归与构建编译验收

**Files:**
- 全局验证与打包

- [ ] **Step 1: 运行全量 Vitest 测试套件**

Run: `cd zw-insight-app && npm test`
Expected: 24 个测试文件、172+ 项测试 100% 全部通过。

- [ ] **Step 2: 运行生产 H5 打包编译验证**

Run: `cd zw-insight-app && npm run build:h5`
Expected: 编译成功，dist/build/h5 生成完成，退出码 0。

- [ ] **Step 3: 最终整理与提交**

```bash
git status
git commit -m "chore(mobile): 完成移动端视觉全面工业化升级与全量验收"
```
