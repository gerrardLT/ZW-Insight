# ZW-Insight 前端业务模块测试用例枚举矩阵

> 生成日期：2026-08-14（基于 main 分支源码逐页深读实证）
> 范围：zw-insight-web PC 端 21 个模块 / 104 个页面（router/index.ts 全景），共 **1,123 个测试用例**（页面级 1,048 + 跨模块集成 75）
> 方法论：每页用例从 view 源码实证枚举（表单 rules、状态条件渲染、按钮禁用条件、金额联动计算、分页/搜索参数、弹窗交互、审批动作），禁止凭空想象；「现有覆盖」列标注实测映射
> 覆盖图例：**L1**=src/__tests__ 单元测试 · **L5-API**=e2e/api-tests（vitest 356 例）· **L5-一致性**=e2e/consistency（53 例字段级比对）· **L5-UI**=e2e/tests/real（29 例）· **无**=任何层级未覆盖
> 用例 ID 规则：A=核心主链（项目/投标/合同/预算）、B=支出域（材料/机械/劳务/分包/采购）、C=财务现场域（财务/现场/人事/档案/看板）、D=平台支撑域（登录/系统/基础数据/消息/工作流/平台）

---

## 一、总览统计

| 分组 | 模块 | 页面数 | 页面用例 | 集成用例 | 合计 | 已有覆盖 | 覆盖占比 |
|---|---|---:|---:|---:|---:|---:|---:|
| A | 项目/投标/合同/预算 | 14 | 170 | 19 | 189 | 62 | ≈33% |
| B | 材料/机械/劳务/分包/采购 | 24 | 233 | 23 | 256 | 62 | ≈24% |
| C | 财务/现场/人事/档案/看板 | 30 | 326 | 18 | 344 | 100 | ≈29% |
| D | 登录/系统/基础数据/消息/工作流/平台 | 36 | 332 | 21 | 353 | 132 | ≈37% |
| **合计** | **21 模块** | **104** | **1,061** | **81** | **1,123** | **356** | **≈32%** |

**覆盖结构特征**（实测）：
- 现有覆盖集中在 L5-一致性（约 15 个列表页字段级比对）与 L5-API（正向 CRUD 冒烟，submit 类普遍容忍 200/400/500 弱断言）
- **表单校验、状态条件渲染、金额联动、库存校验、负向守卫类用例覆盖率接近 0**；L5-UI 交互级仅 3 页面
- 财务域覆盖率最低（20.7%），且最复杂逻辑（多合同路由、封账拦截、结算聚合）恰在零覆盖区

---

# 分组 A：核心业务主链（189 例）

## A-1 项目管理（/project，4 页，46+5 例，覆盖≈52%）

**业务概述**：项目全生命周期入口，源码（index.vue statusMap）实证 8 状态机：DRAFT→FILED→TENDERING→WON→CONSTRUCTION→COMPLETED→CLOSING→CLOSED。列表按状态驱动操作可用性（仅 DRAFT 可编辑/提交/删除，仅 COMPLETED 可发起结项）；结项有 close-check 预检门槛，通过后发起 Flowable 审批。表单含业主单位远程搜索与签约公司联动，另含项目成员/角色管理子组件。

### A1 项目列表（/project/list · views/project/index.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A1-01 | 首屏加载默认分页 | 功能 | 已登录，存在项目数据 | 进入列表页 | GET /v1/project/page（pageNum=1,pageSize=10），表格渲染 8 列 | L5-API/L5-UI |
| A1-02 | 名称搜索重置页码 | 功能 | 第 2 页 | 输入名称回车或点搜索 | pageNum 重置为 1 并重新请求 | L5-UI + L1 project-index-matrix.component.test.ts（2026-08-18） |
| A1-03 | 状态筛选 8 枚举 | 功能 | 各状态数据齐备 | 依次选 DRAFT…CLOSED 查询 | 请求带 status，结果仅含对应状态 | L1 project-index-matrix.component.test.ts（2026-08-18，status 参数下发+8 枚举源码静态钉住） |
| A1-04 | 重置清空条件 | 功能 | 已设置筛选 | 点重置 | 条件清空、pageSize=10 重载 | L1 project-index-matrix.component.test.ts（2026-08-18） |
| A1-05 | 分页 size/页码切换 | 边界 | total>20 | 切 50/页、跳页 | page-sizes=[10,20,50,100] 生效 | L1 project-index-matrix.component.test.ts（2026-08-18，page-sizes 配置钉住） |
| A1-06 | DRAFT 行显示编辑/提交/删除 | 功能 | 存在草稿项目 | 观察操作列 | 仅 DRAFT 渲染三按钮+查看 | L1 project-index-matrix.component.test.ts（2026-08-18） |
| A1-07 | COMPLETED 行显示结项按钮 | 功能 | 存在已竣工项目 | 观察操作列 | 仅 COMPLETED 显示「结项」 | L1 project-index-matrix.component.test.ts（2026-08-18） |
| A1-08 | 非草稿行无编辑/提交/删除 | 负向 | FILED/WON 等行 | 观察操作列并直调 API | UI 无按钮；API submit/delete 返回业务错误。**2026-08 E2E 实证**：submit 拦截 code=500「仅草稿状态可提交」；DELETE **无状态守卫**（FILED 可删，缺陷现状钉住） | L5-API + L1 project-index-matrix.component.test.ts（2026-08-18，UI 面）+ E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；FILED 后操作列收缩 + resubmit 拦截断言） |
| A1-09 | 提交二次确认+取消 | 功能 | DRAFT 行 | 点提交→弹窗取消 | 不发 submit 请求，状态不变 | L1 project-index-matrix.component.test.ts（2026-08-18，取消不发请求断言） |
| A1-10 | 提交成功状态流转 | 集成 | DRAFT 行 | 确认后提交 | POST /{id}/submit 成功，刷新后状态变更。**2026-08 E2E 实证修正**：项目提交为直批——DRAFT→submit 立即 FILED（无 Flowable 待办，A-X2 预期修正） | L5-API + E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；UI 提交→已报备标签+请求仅一次计数断言） |
| A1-11 | 结项预检不满足拦截 | 负向 | COMPLETED 但有未结清事项 | 点结项 | close-check allPassed=false，alert 展示 failedReasons（；分隔），不发 close | L5-API + E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；演示 COMPLETED 项目 alert 动态 reasons 断言 + closePostCount=0） |
| A1-12 | 结项预检通过发起审批 | 集成 | 预检全通过 | 确认后结项 | POST /{id}/close 成功，状态→CLOSING | DATA 受阻（tasks.md 登记 2026-08-18：无 allPassed=true 演示项目前提） |
| A1-13 | 删除草稿成功刷新 | 功能 | DRAFT 行 | 确认删除 | DELETE 成功，列表刷新 | L5-API + E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；UI 确认删除→行消失） |
| A1-14 | 状态标签文案映射一致 | 一致性 | 各状态行 | 比对标签 | 8 状态中文标签与 statusMap 一致 | L5-一致性 + L1 project-index-matrix.component.test.ts（2026-08-18，statusMap 源码钉住） |

### A2 项目表单（/project/create、/project/edit/:id · views/project/form.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A2-01 | 5 项必填校验 | 负向 | 新增页 | 逐项清空 projectName/性质/类型/业主/签约公司后保存 | 分别提示对应必填文案，不发请求 | L5-UI |
| A2-02 | 完整新增保存 | 功能 | 业主/公司基础数据存在 | 填全→保存 | POST /v1/project 成功，跳 /project/list | L5-API/L5-UI + E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；业主远程搜索+签约公司联动+POST 硬断言+跳列表） |
| A2-03 | 项目编号只读 | 功能 | 新增页 | 尝试编辑编号 | 输入框 disabled，placeholder「系统自动生成」 | L1 project-form-detail-matrix.component.test.ts（2026-08-18） |
| A2-04 | 业主单位远程搜索 | 功能 | 新增页 | 输入关键字 | 触发 GET /v1/basedata/owner/list?ownerName=，loading 态 | L1 project-form-detail-matrix.component.test.ts（2026-08-18）+ E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；A2-02 含 UI 远程搜索） |
| A2-05 | 选中后同步名称快照 | 功能 | 选项已加载 | 选中业主/签约公司 | ownerCompanyName/signingCompanyName 同步回填 | L1 project-form-detail-matrix.component.test.ts（2026-08-18） |
| A2-06 | 编辑回显含业主注入 | 一致性 | 已有项目 | 进入编辑页 | 表单回显；业主单位强制注入下拉选项 | L5-一致性 |
| A2-07 | 雪花 ID 字符串传递 | 边界 | 编辑 >2^53 的 ID | 直接 URL 访问 | getProjectDetail 以字符串传参，无精度丢失 | L1 project-form-detail-matrix.component.test.ts（2026-08-18，19 位超 MAX_SAFE_INTEGER 断言） |
| A2-08 | 预算金额非负 | 边界 | 新增页 | 输入负数 | input-number min=0 拦截 | L1 project-form-detail-matrix.component.test.ts（2026-08-18，min=0 precision=2 配置钉住） |
| A2-09 | 是否招标默认「否」 | 功能 | 新增页 | 观察单选 | needTender 默认 0 | L5-API + L1 project-form-detail-matrix.component.test.ts（2026-08-18） |
| A2-10 | 校验失败不发请求 | 负向 | 必填缺失 | 点保存 | 无网络请求，submitLoading 不置位 | L1 project-form-detail-matrix.component.test.ts（2026-08-18，api mock 零调用断言） |
| A2-11 | 更新走 PUT 并带 id | 功能 | 编辑页 | 修改后保存 | PUT /v1/project/{id}，提示更新成功 | L5-API |
| A2-12 | 取消/返回不落库 | 功能 | 已修改字段 | 点取消/返回 | 跳列表，无写请求 | L1 project-form-detail-matrix.component.test.ts（2026-08-18，无写请求断言） |

### A3 项目详情（/project/detail/:id · views/project/detail.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A3-01 | 字段级展示一致 | 一致性 | 已有项目 | 比对 12 项描述字段 | 与 GET /{id} 返回一致 | L5-一致性 |
| A3-02 | CLOSING 状态标签缺失 | 负向 | 项目处于 CLOSING | 打开详情 | detail statusMap 无 CLOSING，回退显示原始枚举串（源码实证缺陷） | L1 project-form-detail-matrix.component.test.ts（2026-08-18，缺陷钉住） |
| A3-03 | URL tab 直达 | 功能 | 已有项目 | 访问 ?tab=team | 初始激活「项目团队」tab | L1 project-form-detail-matrix.component.test.ts（2026-08-18） |
| A3-04 | 返回列表 | 功能 | 详情页 | 点返回 | 跳 /project/list | L1 project-form-detail-matrix.component.test.ts（2026-08-18） |
| A3-05 | 非法/越权 id | 负向 | 无权限或不存在 id | 直接 URL 访问 | request 拦截器统一错误提示，页面空态 | E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；非法 id 详情空态） |
| A3-06 | 标题含项目名称 | 功能 | 已有项目 | 观察卡片头 | 「项目详情：{projectName}」 | L5-UI + L1 project-form-detail-matrix.component.test.ts（2026-08-18） |
| A3-07 | 预算金额 0/空显示 | 边界 | budgetAmount=0 | 观察字段 | 显示 0 而非空 | L1 project-form-detail-matrix.component.test.ts（2026-08-18，el-descriptions cell 相邻断言） |
| A3-08 | 切 tab 触发成员加载 | 集成 | 详情已加载 | 点项目团队 | ProjectMember 挂载即请求成员列表 | L1 project-member-matrix.component.test.ts（2026-08-18，?tab=team 直达挂载即请求） |

### A4 项目团队（详情 tab · views/project/components/ProjectMember.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A4-01 | 成员列表加载 | 功能 | 项目有成员 | 进团队 tab | GET /{projectId}/member 分页渲染 | L5-API |
| A4-02 | 按角色筛选 | 功能 | 7 类角色数据 | 选角色 | 请求带 role，pageNum 重置 1 | L1 project-member-matrix.component.test.ts（2026-08-18） |
| A4-03 | 添加成员必填校验 | 负向 | 打开添加弹窗 | 不选用户/角色点确定 | 「请选择用户」「请选择至少一个角色」(min:1) | L1 project-member-matrix.component.test.ts（2026-08-18，不发 POST + loading 不置位） |
| A4-04 | 用户远程搜索 | 功能 | 添加弹窗 | 输入姓名 | getUserPage(realName,pageSize=20)，选项含部门后缀 | L1 project-member-matrix.component.test.ts（2026-08-18，含空查询不请求断言） |
| A4-05 | 添加成员成功 | 功能 | 用户可选 | 选用户+多角色→确定 | POST member 成功，关弹窗刷新 | L5-API + E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；UI 远程搜索用户+双角色，行可见断言） |
| A4-06 | 重复添加拦截 | 负向 | 用户已是成员 | 再次添加同一用户 | 后端报错，拦截器 Toast，弹窗保留 | E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；code=400「已是本项目成员」+弹窗保留断言） |
| A4-07 | 变更角色空选拦截 | 负向 | 变更角色弹窗 | 清空角色点确定 | 前端 warning，不发 PUT | L1 project-member-matrix.component.test.ts（2026-08-18，「请至少选择一个角色」warning 断言） |
| A4-08 | 变更角色成功 | 功能 | 成员存在 | 调整角色确定 | PUT /member/{userId}/roles，标签刷新 | E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；UI 变更→资料员标签刷新） |
| A4-09 | 移除确认与取消 | 负向 | 成员存在 | 点移除→取消 | 无 DELETE 请求 | L1 project-member-matrix.component.test.ts（2026-08-18） |
| A4-10 | 移除成功 | 功能 | 成员存在 | 确认移除 | DELETE 成功刷新 | L5-API + E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；UI 确认→DELETE→行消失） |
| A4-11 | 分页尺寸 | 边界 | 成员>10 | 切换 size | page-sizes=[10,20,50] 生效 | L1 project-member-matrix.component.test.ts（2026-08-18） |
| A4-12 | 多角色标签渲染 | 一致性 | 成员持多角色 | 观察角色列 | 每角色一 tag，7 角色中文映射正确 | L1 project-member-matrix.component.test.ts（2026-08-18） |

### A-X 项目跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| A-X1 | 投标中标联动项目状态 | 集成 | 项目报备→投标报名→开标记录 isWon=1 | register→WON，项目状态联动 WON | L5-API + E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；开标 isWon=1→报名 WON+项目 WON 联动断言补齐） |
| A-X2 | 项目提交审批闭环 | 集成 | DRAFT 提交→Flowable 待办→审批通过 | 项目 FILED，待办消失。**2026-08 E2E 实证修正**：项目提交为直批（DRAFT→submit 立即 FILED，无 Flowable 待办环节），账本预期按实证修正 | E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；A1-10 用例实证直批钉住） |
| A-X3 | 结项审批全链路 | 集成 | close-check 通过→close→审批通过/驳回 | 通过→CLOSED；驳回→回 COMPLETED | DATA 受阻（tasks.md 登记 2026-08-18：预检全通过数据前提缺失，同 A1-12） |
| A-X4 | 项目删除引用拦截 | 负向 | 删除已挂合同/预算的项目 | 后端引用拦截，前端 Toast。**2026-08 E2E 实证修正**：后端 DELETE **无引用检查且无状态守卫**（挂报名引用仍 code=200 放行删除，与账本预期不符，缺陷现状钉住） | E2E a1-project.spec.ts（真实模式，2026-08-18 全绿；挂报名引用 DELETE 放行现状钉住）+ L1 project-index-matrix.component.test.ts（delete reject 不吞错） |
| A-X5 | 成员角色与数据权限 | 权限 | 以项目经理/普通成员分别查看项目数据 | 按角色控制数据可见范围 | L1(仅单测) |

## A-2 投标管理（/tender，2 页，24+4 例，覆盖≈50%）

**业务概述**：投标报名登记（REGISTERED→SUBMITTED→WON/LOST 四态）与企业/人员证件管理。报名以弹窗表单 CRUD，提交后进入投标状态；证件由后端按到期日计算 VALID/EXPIRING/EXPIRED 三态。API 层还有任务/费用/保证金/开标接口，但两个视图未覆盖（由其他页面消费）。

### A5 投标报名（/tender/register · views/tender/register.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A5-01 | 列表加载与状态标签 | 功能 | 有报名数据 | 进入页面 | 4 态标签（报名中/已投标/中标/未中标），保证金千分位 | L5-API/L5-一致性 + L1 tender-matrix.component.test.ts（2026-08-18） |
| A5-02 | 项目筛选与重置 | 功能 | 多项目数据 | ProjectSelector 选项目→搜索/重置 | page=1 带 projectId 查询；重置清空 | L1 tender-matrix.component.test.ts（2026-08-18） |
| A5-03 | 新增必填校验 | 负向 | 打开新增弹窗 | 项目/业主单位留空点确定 | 「请选择项目」「请输入业主单位」 | L1 tender-matrix.component.test.ts（2026-08-18，文案钉住+不发创建请求） |
| A5-04 | 完整新增报名 | 功能 | 项目存在 | 填全 8 字段确定 | POST register 成功，状态 REGISTERED | L5-API + E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；UI 完整新增含项目远程搜索+「报名中」标签） |
| A5-05 | 保证金精度边界 | 边界 | 新增弹窗 | 输入负数/3 位小数 | min=0、precision=2 生效 | L1 tender-matrix.component.test.ts（2026-08-18） |
| A5-06 | 编辑回显（detail 合并） | 一致性 | 已有记录 | 点编辑 | 先 GET /{id} 再与 defaultForm 合并回显 | L5-API/L5-一致性 |
| A5-07 | 提交仅 REGISTERED 可见 | 功能 | 各状态行 | 观察操作列 | 仅 REGISTERED 显示提交/删除 | L1 tender-matrix.component.test.ts（2026-08-18）+ E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；SUBMITTED 后提交/删除按钮消失） |
| A5-08 | 提交确认与取消 | 负向 | REGISTERED 行 | 提交→取消 | 不发 PUT /{id}/submit | L1 tender-matrix.component.test.ts（2026-08-18） |
| A5-09 | 提交成功状态流转 | 集成 | REGISTERED 行 | 确认提交 | 状态→SUBMITTED，按钮消失 | L5-API + E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；UI 提交→「已投标」标签+按钮消失+项目 TENDERING 联动 A-X6） |
| A5-10 | 非法状态提交拦截 | 负向 | WON/LOST 记录 | 直调 submit API | 后端拒绝。**2026-08 E2E 实证**：SUBMITTED resubmit 同样拦截 code=500「仅报名状态可提交」 | E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；API 直调拦截断言） |
| A5-11 | 开标日期早于报名日期 | 边界 | 新增弹窗 | openDate<registerDate | 前端无校验（源码实证），验证后端是否拦截。**2026-08 E2E 实证**：后端也接受（code=200），现状钉住 | L1 tender-matrix.component.test.ts（2026-08-18，源码无 validator 钉住）+ E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；后端接受现状钉住+当场清理） |
| A5-12 | 分页参数命名 page/size | 一致性 | 数据>10 | 翻页 | 该页用 page/size（其余模块 pageNum/pageSize），请求正确 | E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；列表请求 page/size 口径抓包钉住） |
| A5-13 | 删除仅草稿态 | 负向 | SUBMITTED 行 | 观察操作列 | 无删除按钮；直调 DELETE 应被拒。**2026-08 E2E 实证**：DELETE code=500「仅报名状态可删除」（SUBMITTED/WON/LOST 均不可删） | L1 tender-matrix.component.test.ts（2026-08-18，UI 按钮门禁）+ E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；API 直调拦截断言） |
| A5-14 | 编辑任意状态可入 | 负向 | WON 行 | 点编辑并保存 | 前端未限制（源码实证），验证后端状态校验。**2026-08 E2E 实证**：PUT 无状态守卫，SUBMITTED 编辑放行（缺陷现状钉住） | E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；PUT 放行现状钉住） |

### A6 证件管理（/tender/certificate · views/tender/certificate.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A6-01 | 列表加载三态标签 | 功能 | 有证件数据 | 进入页面 | VALID/EXPIRING/EXPIRED 标签渲染。**2026-08 E2E 实证**：后端 status 为 Integer 1/0 且无到期计算，列表恒显示「已过期」（API-GAP） | L5-一致性 + API-GAP 受阻（tasks.md 登记 2026-08-18；E2E a2-tender.spec.ts 现状钉住断言） |
| A6-02 | 三条件筛选 | 功能 | 数据齐备 | 证件名称/持证人/状态查询 | pageNum 重置 1，条件生效。**2026-08 E2E 实证**：前端 certName/holderName/status 与后端 personName/certificateType 参数名脱节，筛选实际失效（API-GAP） | L1 tender-matrix.component.test.ts（2026-08-18，前端行为钉住）+ API-GAP 受阻（tasks.md 登记 2026-08-18） |
| A6-03 | 新增必填校验 | 负向 | 打开弹窗 | 名称/编号/持证人留空 | 3 条必填提示 | L1 tender-matrix.component.test.ts（2026-08-18，文案钉住+不发请求） |
| A6-04 | 新增证件成功 | 功能 | — | 填全确定 | POST /certificate/person 成功 | L5-API |
| A6-05 | 编辑回显与更新 | 功能 | 已有证件 | 编辑→改日期→确定 | PUT 成功刷新。**2026-08 E2E 实证**：前端 expiryDate/issueOrgan 与后端 expireDate 等字段脱节，回显/更新链路失真（API-GAP） | API-GAP 受阻（tasks.md 登记 2026-08-18） |
| A6-06 | 删除确认与成功 | 功能 | 已有证件 | 确认删除 | DELETE /certificate/{type}/{id} | 无 |
| A6-07 | 到期状态后端计算 | 集成 | 到期日临近/已过 | 保存后查列表 | 后端按日期算 EXPIRING/EXPIRED。**2026-08 E2E 实证**：后端无到期日计算逻辑（status 恒 Integer，无枚举映射），预期不成立（API-GAP） | API-GAP 受阻（tasks.md 登记 2026-08-18） |
| A6-08 | 到期日早于发证日 | 边界 | 新增弹窗 | expiryDate<issueDate | 前端无校验（源码实证），验证后端行为 | L1 tender-matrix.component.test.ts（2026-08-18，源码无校验现状钉住） |
| A6-09 | 企业证书入口不可达 | 负向 | — | 页面全链路操作 | row.type 恒缺省→恒走 person；company 分支前端无法触达（源码实证盲点） | 仅 L5-API 直调 + L1 tender-matrix.component.test.ts（2026-08-18，row.type \|\| 'person' 源码钉住） |
| A6-10 | 分页边界 | 边界 | 数据>10 | 切换 size/页码 | [10,20,50] 生效。**2026-08 E2E 实证**：前端 pageNum/pageSize vs 后端 @RequestParam page/size，前端分页参数被忽略（API-GAP） | L1 tender-matrix.component.test.ts（2026-08-18，page-sizes 配置钉住）+ API-GAP 受阻（tasks.md 登记 2026-08-18；E2E 现状钉住断言） |

### A-X 投标跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| A-X6 | 报名提交→项目招标中 | 集成 | REGISTERED→提交 | 项目状态联动 TENDERING | E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；提交后项目状态 TENDERING 联动断言） |
| A-X7 | 开标中标双向联动 | 集成 | 创建开标记录 isWon=1 | register→WON 且项目→WON | L5-API + E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；开标 isWon=1→报名 WON+项目 WON 双联动断言补齐） |
| A-X8 | 保证金申请-缴纳-退还链 | 集成 | deposit/apply→fee 确认付款→deposit/return | 三环节金额一致、状态流转正确 | L5-API(弱断言) |
| A-X9 | 中标后合同入口 | 集成 | 中标项目创建施工合同 | 合同可选该项目，项目进入施工链路 | E2E a2-tender.spec.ts（真实模式，2026-08-18 全绿；前提钉住——开标 isWon=1 后项目 WON，进入施工链路前置状态实证） |

## A-3 合同管理（/contract，4 页，52+5 例，覆盖≈29%）

**业务概述**：施工合同（收入合同），五态机 DRAFT→SUBMITTED→EFFECTIVE→SETTLED/CLOSED。表单含合同明细行（数量×单价实时合计）；BOQ 支持 Excel 上传解析为树形清单（平铺数据前端 buildTree）；产值上报支持「按清单行/纯金额」双模式，仅 EFFECTIVE 合同可报，提交走 Flowable 审批（DRAFT/REJECTED 可再提交）。

### A7 施工合同列表（/contract/list · views/contract/index.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A7-01 | 列表加载与金额格式 | 功能 | 有合同数据 | 进入页面 | contractAmount 千分位 2 位小数 | L5-API/L5-一致性 |
| A7-02 | 项目远程筛选 | 功能 | 多项目合同 | 输入项目名搜索后筛选 | getProjectList(projectName)，带 projectId 查询 | 无 |
| A7-03 | formatMoney 边界 | 边界 | 金额 null/0 | 观察列 | 空→「-」，0→「0.00」 | 无 |
| A7-04 | DRAFT 三按钮渲染 | 功能 | 各状态数据 | 观察操作列 | 仅 DRAFT 显示编辑/提交/删除 | 无 |
| A7-05 | 提交审批流转 | 集成 | DRAFT 合同 | 确认提交 | PUT /{id}/submit→SUBMITTED | L5-API |
| A7-06 | 非法状态提交拦截 | 负向 | EFFECTIVE 合同 | 直调 submit | 后端拒绝 | 无 |
| A7-07 | 删除草稿 | 功能 | DRAFT 合同 | 确认删除 | DELETE 成功刷新 | 无 |
| A7-08 | 查看入口即编辑页 | 负向 | 任意状态 | 点查看 | handleView 跳 /contract/edit/:id（源码实证：无只读视图，非草稿亦可进编辑表单） | 无 |
| A7-09 | 打印按钮渲染 | 功能 | 任意行 | 观察操作列 | PrintButton business-type=CONTRACT 可用 | 无 |
| A7-10 | 状态标签映射 | 一致性 | 5 态数据 | 比对 | 与 statusMap 一致 | L5-一致性 |
| A7-11 | 分页 | 边界 | 数据>10 | 切 size/翻页 | [10,20,50,100] 生效 | 无 |
| A7-12 | 重置 | 功能 | 已筛选 | 点重置 | projectId/status 清空重载 | 无 |

### A8 施工合同表单（/contract/create、/contract/edit/:id · views/contract/form.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A8-01 | 三项必填校验 | 负向 | 新增页 | 项目/甲方/金额留空保存 | 3 条必填提示，不发请求 | 无 |
| A8-02 | 默认值验证 | 功能 | 新增页 | 观察 | contractType=REGISTER、taxRate=9、合同编号 disabled | 无 |
| A8-03 | 明细行增删与合计 | 功能 | 新增页 | 加行→输数量/单价→删行 | 合计=quantity×unitPrice toFixed(2)，行删除正确 | L5-API(明细 API 层) |
| A8-04 | 4 位小数精度计算 | 边界 | 明细行 | quantity=0.0001×unitPrice=9999.9999 | 乘积无浮点溢出，显示 2 位 | 无 |
| A8-05 | 空明细不调保存接口 | 功能 | 无明细行 | 保存 | detailList 为空时跳过 saveContractDetails | 无 |
| A8-06 | 编辑回显含明细与项目注入 | 一致性 | 已有合同+明细 | 进编辑页 | 主表+明细回显，项目注入下拉 | 无 |
| A8-07 | 雪花 ID 字符串传递 | 边界 | 大 ID 合同 | URL 直达编辑 | 字符串传参无精度丢失 | 无 |
| A8-08 | 合同金额≠明细合计仍可保存 | 负向 | 明细合计与金额不等 | 保存 | 前端无一致性校验（源码实证），验证后端是否拦截 | 无 |
| A8-09 | 开工晚于竣工日期 | 边界 | 新增页 | startDate>endDate | 前端无校验（源码实证），验证后端行为 | 无 |
| A8-10 | 变更/补充合同类型 | 功能 | 新增页 | 选 CHANGE/SUPPLEMENT | 保存值正确 | 无 |
| A8-11 | 税率边界 | 边界 | 新增页 | 输 101/负数 | min=0 max=100 拦截 | 无 |
| A8-12 | 取消不落库 | 负向 | 已修改 | 点取消 | 无写请求，返回列表 | 无 |

### A9 BOQ 上传（/contract/boq/:contractId · views/contract/boq-upload.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A9-01 | 页面加载合同信息与已有清单 | 功能 | 合同已上传清单 | 进入页面 | descriptions 展示；树表 default-expand-all | 无 |
| A9-02 | 非 xlsx 格式拒绝 | 负向 | — | 选 .xls/.csv | 「仅支持 .xlsx 格式文件」，clearFiles | 无 |
| A9-03 | 大写 .XLSX 被拒 | 负向 | — | 选 .XLSX | endsWith('.xlsx') 大小写敏感→误拒（源码实证边界） | 无 |
| A9-04 | 超 20MB 拒绝 | 边界 | — | 选 21MB 文件 | 「文件大小不能超过 20MB」 | 无 |
| A9-05 | 文件数量超限 | 边界 | 已选 1 文件 | 再选一个 | 「仅允许上传一个文件」warning | 无 |
| A9-06 | 未选文件按钮禁用 | 功能 | 无文件 | 观察 | 「开始上传解析」disabled | 无 |
| A9-07 | 上传解析成功统计 | 功能 | 合法 xlsx | 上传 | POST /v1/contracts/{id}/boq/upload，展示总条目/层级数/合计金额 | 无 |
| A9-08 | 解析失败错误提示 | 负向 | 内容非法 xlsx | 上传 | 拦截器 Toast，uploading 复位 | 无 |
| A9-09 | 平铺数据建树与孤儿回落 | 边界 | 后端返回平铺 | 加载树 | 按 parentId 建树；父缺失回落根节点 | 无 |
| A9-10 | 金额/数量格式化 | 一致性 | 清单有值/空 | 观察列 | 金额 2 位、数量 ≤4 位、空显示「-」 | 无 |
| A9-11 | 清除清单确认与取消 | 负向 | 有清单 | 清除→取消 | 不发 DELETE | 无 |
| A9-12 | 清除后状态复位 | 功能 | 有清单 | 确认清除 | DELETE 成功，树/统计清空，清除按钮隐藏 | 无 |
| A9-13 | 上传超时配置 | 边界 | 超大清单文件 | 上传 | timeout=120s 生效 | 无 |

### A10 产值上报（/contract/output-report · views/contract/output-report.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A10-01 | 列表加载与四态标签 | 功能 | 有上报数据 | 进入页面 | DRAFT/SUBMITTED/APPROVED/REJECTED 标签 | 无（06 路径失配） |
| A10-02 | 项目→合同级联仅生效合同 | 集成 | 项目有多状态合同 | 选项目 | contractId 清空，下拉仅 status=EFFECTIVE（pageSize=100） | 无 |
| A10-03 | 新增必填校验 | 负向 | 打开弹窗 | 项目/合同/期间留空 | 3 条必填提示 | 无 |
| A10-04 | 本期产值须大于 0 | 负向 | 纯金额模式 | currentOutput=0 保存 | 「本期产值须大于0」(min:0.01) | 无 |
| A10-05 | 无 BOQ 合同仅纯金额 | 功能 | 合同无清单 | 选合同 | 「按清单行」disabled+提示文案，fillMode=amount | 无 |
| A10-06 | 有 BOQ 自动切清单模式 | 集成 | 合同有清单 | 选合同 | getBoqFlat 加载，自动 fillMode=boq | 无 |
| A10-07 | 完成量实时重算合计 | 功能 | boq 模式 | 输入本期完成量 | 行金额=q×p toFixed(2)，合计=Σ行金额 toFixed(2) | 无 |
| A10-08 | 行金额舍入精度 | 边界 | 单价 0.33×量 3 | 计算 | 0.99 无浮点误差 | 无 |
| A10-09 | 全部完成量为 0 拦截 | 负向 | boq 模式 | 不填任何量保存 | 「请至少填写一条清单行的本期完成量」，不发请求 | 无 |
| A10-10 | 草稿保存 details 过滤 | 功能 | 部分行有量 | 保存 | details 仅含 reportQuantity>0 行，含 amount | 无 |
| A10-11 | 提交按钮状态渲染 | 功能 | 各状态行 | 观察操作列 | 仅 DRAFT/REJECTED 显示提交 | 无 |
| A10-12 | 提交审批闭环 | 集成 | DRAFT 上报单 | 确认提交→Flowable 审批 | 「已提交审批，审批通过后生效」；通过后 APPROVED | 无 |
| A10-13 | 切换项目状态复位 | 功能 | 已选合同 | 换项目 | 合同/BOQ/金额全部复位 | 无 |
| A10-14 | 重置 | 功能 | 已筛选 | 点重置 | 条件与合同选项清空重载 | 无 |
| A10-15 | 分页 | 边界 | 数据>10 | 翻页 | [10,20,50] 生效 | 无 |

### A-X 合同跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| A-X10 | 合同审批通过→可报产值 | 集成 | 合同 DRAFT→提交→审批通过 EFFECTIVE→产值上报选合同 | 仅 EFFECTIVE 出现在上报下拉 | 无 |
| A-X11 | BOQ→产值清单行联动 | 集成 | 上传 BOQ→产值按清单行填报→审批通过 | 清单 completedQuantity 随审批累加 | 无 |
| A-X12 | 驳回后可重新提交 | 集成 | 产值上报审批驳回 | 状态 REJECTED，提交按钮复现 | 无 |
| A-X13 | 预算 BLOCK 拦截支出合同 | 集成 | 项目配 BLOCK→创建支出类合同并提交 | 提交被拦截并提示超预算（施工收入合同不受控，须区分验证） | 无 |
| A-X14 | 产值累计与竣工结算 | 集成 | 多期上报 APPROVED | cumulativeOutput 逐期累加，为结算提供依据 | 无 |

## A-4 预算管理（/budget，4 页，48+5 例，覆盖≈23%）

**业务概述**：目标成本编制（budgetType=ORIGINAL，同项目唯一）、变更单（5 态含 WITHDRAWN，走 Flowable 提交/撤回）与管控配置（BLOCK 禁止提交/WARN_ONLY 仅提醒/EXEMPT 免控，阈值 50–99% 滑杆）。变更表单仅可选 APPROVED 预算，调整后金额=原金额+调整金额实时计算。

### A11 预算编制列表（/budget/list · views/budget/index.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A11-01 | 列表加载与状态标签 | 功能 | 有预算数据 | 进入页面 | 已批准/审批中/草稿三态标签 | L5-API/L5-一致性 |
| A11-02 | 项目筛选与重置 | 功能 | 多项目 | 选项目搜索/重置 | 带 projectId 查询；重置重载 | 无 |
| A11-03 | 新增必填校验 | 负向 | 打开弹窗 | 项目/总额留空 | 「请选择项目」「请输入预算总额」 | E2E expense-write-2.spec.ts（真实模式，2026-08-18 全绿；空态确定不发 POST 抓包实证） |
| A11-04 | 创建 payload 固定 ORIGINAL | 一致性 | — | 新增确定 | 请求体 budgetType='ORIGINAL' | L5-API |
| A11-05 | 编辑更新金额 | 功能 | 已有预算 | 改总额确定 | PUT 成功 | L5-API |
| A11-06 | 提交审批流转 | 集成 | DRAFT | 确认提交 | POST /{id}/submit→APPROVING，Flowable 待办。**2026-08 E2E 实证修正**：BudgetService.submit 无流程依赖，直批 APPROVED（非 APPROVING） | L5-API + E2E expense-write-2.spec.ts（真实模式，2026-08-18 全绿；创建 DRAFT→提交直批→明细合计回写总额） |
| A11-07 | 非法状态提交拦截 | 负向 | APPROVED 预算 | 直调 submit | 后端拒绝 | 无 |
| A11-08 | 删除草稿 | 功能 | DRAFT | 确认删除 | DELETE 成功 | 无 |
| A11-09 | 同项目重复目标成本拦截 | 负向 | 项目已有预算 | 再创建 | 后端拒绝（唯一性约束） | L5-API |
| A11-10 | 金额边界 | 边界 | 弹窗 | 负数/3 位小数 | min=0、precision=2 生效 | 无 |
| A11-11 | 编辑按钮不受状态限制 | 负向 | APPROVED/APPROVING 行 | 点编辑保存 | 前端始终显示编辑（源码实证），验证后端是否拦截 | 无 |
| A11-12 | 分页 | 边界 | 数据>10 | 翻页 | [10,20,50] 生效 | 无 |

### A12 目标成本变更列表（/budget/change · views/budget/change/index.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A12-01 | 列表加载五态标签 | 功能 | 变更数据齐备 | 进入页面 | DRAFT/SUBMITTED/APPROVED/REJECTED/WITHDRAWN | 无 |
| A12-02 | 项目切换自动搜索 | 功能 | 多项目 | ProjectSelector 切换 | @change 直接触发 handleSearch | 无 |
| A12-03 | 调整总额红绿着色 | 一致性 | 正/负调整单 | 观察列 | <0 红、>0 绿、千分位 | 无 |
| A12-04 | 状态驱动操作集 | 功能 | 各状态行 | 观察操作列 | DRAFT:编辑/提交/删除；SUBMITTED:撤回；APPROVED:仅查看 | 无 |
| A12-05 | 提交进入审批 | 集成 | DRAFT | 确认提交 | POST /{id}/submit→SUBMITTED | 无 |
| A12-06 | 撤回成功 | 功能 | SUBMITTED | 确认撤回 | POST /{id}/withdraw→WITHDRAWN | 无 |
| A12-07 | 非法状态撤回拦截 | 负向 | DRAFT/APPROVED | 直调 withdraw | 后端拒绝 | 无 |
| A12-08 | 删除草稿确认 | 功能 | DRAFT | 确认删除 | DELETE 成功「删除后不可恢复」文案 | 无 |
| A12-09 | 查看带 mode=view | 功能 | 任意行 | 点查看 | 跳 /budget/change/form?id=&mode=view | 无 |
| A12-10 | 状态筛选 | 功能 | 各状态 | 逐状态筛选 | 结果匹配 | 无 |
| A12-11 | 分页 | 边界 | 数据>10 | 翻页 | [10,20,50] 生效 | 无 |

### A13 变更单表单（/budget/change/form · views/budget/change/form.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A13-01 | 必填校验 | 负向 | 新建页 | 项目/变更原因留空 | 「请选择所属项目」「请输入变更原因」 | 无 |
| A13-02 | 空明细拦截 | 负向 | 必填已填 | 不加明细行保存 | 「请至少添加一条变更明细」 | 无 |
| A13-03 | 明细未选科目拦截 | 负向 | 有空明细行 | 保存 | 「请为每一行选择原预算明细」 | 无 |
| A13-04 | 原预算仅 APPROVED | 集成 | 项目有草稿+已批准预算 | 选项目 | 下拉仅 APPROVED（pageSize=100） | 无 |
| A13-05 | 选明细带出科目与原金额 | 功能 | 预算有明细 | 选原预算明细 | 带出 costCategory/costSubcategory/itemName/originalAmount | 无 |
| A13-06 | 调整后金额计算 | 功能 | 明细行 | 输调整金额 | adjustedAmount=原+调整 toFixed(2)，负值递减 | 无 |
| A13-07 | 调整总额聚合 | 功能 | 多明细行 | 各行输入 | totalAdjustAmount=ΣadjustAmount，红绿着色 | 无 |
| A13-08 | 保存草稿 | 功能 | 合法表单 | 保存草稿 | POST create（含 details/totalAdjustAmount）→回列表 | L5-API(弱) |
| A13-09 | 提交审批组合操作 | 集成 | 新建合法表单 | 提交审批→确认 | create+submit 两连发→SUBMITTED→回列表 | 无 |
| A13-10 | 取消确认不发请求 | 负向 | 提交审批弹窗 | 取消 | 无 create/submit 请求 | 无 |
| A13-11 | 编辑回显（budgetId 二次回填） | 一致性 | 已有变更单 | 进编辑页 | 项目/预算/明细完整回显（handleProjectChange 清空后回填） | 无 |
| A13-12 | 查看模式只读 | 功能 | mode=view | 观察 | form disabled、无底部按钮、明细纯文本 | 无 |
| A13-13 | 变更原因 500 字上限 | 边界 | 新建页 | 输 501 字 | maxlength=500 截断+字数统计 | 无 |
| A13-14 | 项目无已批准预算 | 边界 | 项目仅草稿预算 | 选项目 | 原预算下拉为空，无法选 | 无 |
| A13-15 | 编辑模式走 update | 功能 | 编辑页 | 保存 | PUT /{id}（updateBudgetChange(id,data)） | 无 |

### A14 预算控制配置（/budget/control-config · views/budget/control-config/index.vue）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| A14-01 | 列表加载与全局默认显示 | 功能 | 有项目级+全局规则 | 进入页面 | projectName 空显示「全局默认」，模式 tag 三色映射 | L5-API |
| A14-02 | 名称/模式筛选 | 功能 | 数据齐备 | 输入名称、选 BLOCK | 条件生效，pageNum 重置 | 无 |
| A14-03 | 控制模式必填 | 负向 | 新建弹窗 | 清空模式确定 | 「请选择控制模式」 | 无 |
| A14-04 | 创建项目级规则 | 功能 | 项目存在 | 选项目+BLOCK+阈值 | POST /v1/budget-control-configs 成功 | 无 |
| A14-05 | 项目留空=全局规则 | 功能 | 新建弹窗 | 不选项目 | payload.projectId=null | 无 |
| A14-06 | 阈值滑杆范围 | 边界 | 弹窗 | 拖动/输入 | min=50 max=99 step=1，默认 80 | 无 |
| A14-07 | 编辑更新 | 功能 | 已有规则 | 改模式/阈值 | PUT 成功 | 无 |
| A14-08 | 删除回落提示 | 功能 | 项目级规则 | 确认删除 | 文案「删除后将回落为全局默认规则」，DELETE 成功 | 无 |
| A14-09 | 同项目重复配置 | 负向 | 项目已有规则 | 再建同项目规则 | 验证后端唯一性拦截 | 无 |
| A14-10 | 分页/数组双兼容 | 边界 | 后端返回数组或分页 | 加载 | res.data?.records 兜底 res.data 数组 | 无 |
| A14-11 | BLOCK 拦截语义端到端 | 集成 | 项目配 BLOCK 且预算将耗尽 | 提交支出单据 | 超阈值提交被拦截，WARN_ONLY 仅提醒、EXEMPT 放行（getEffectiveConfig） | E2E expense-write-2.spec.ts（真实模式，2026-08-18 全绿；UI 层：自置 BLOCK 项目+项目级配置，other-payment 创建被拒含预算语义 + 错误 Toast 可见 + 差集无落库；WARN_ONLY/EXEMPT 分支仍待补） |

### A-X 预算跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| A-X15 | 预算批准→变更可选 | 集成 | 编制提交→审批 APPROVED→变更表单选项目 | 该预算出现在原预算下拉 | 无 |
| A-X16 | 变更审批通过→总额更新 | 集成 | 变更单提交→Flowable 通过 | 预算 totalAmount 按调整总额更新 | 无 |
| A-X17 | 撤回不影响预算 | 集成 | SUBMITTED 变更单撤回 | WITHDRAWN，预算金额不变 | 无 |
| A-X18 | BLOCK 拦截支出合同提交 | 集成 | BLOCK 项目提交超预算支出合同 | 提交被拒并展示预警信息；WARN_ONLY 放行+提醒 | 无 |
| A-X19 | 删除项目级规则回落全局 | 集成 | 删除项目 BLOCK 规则后提交支出 | 行为回落全局默认配置 | 无 |

---

# 分组 B：支出域五模块（256 例）

## B-1 材料库存（/material，5 页）

**业务概述**：入库单（草稿→提交后更新库存与合同累计入库，inbound.vue 提交确认文案实证）；出库分领料 PICK/退货 RETURN 两类型；调拨需双项目且前端禁止同项目；退款记录只读、由退货出库审批自动生成（api/material.ts 注释「无手动创建接口」）。

### B1 到货入库（/material/inbound）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-1-1 | 必填校验 projectId/inboundDate | 负向 | 打开新增弹窗 | 不选项目/日期直接确定 | 提示「请选择项目」「请选择入库日期」 | E2E expense-write-2.spec.ts（真实模式，2026-08-18 全绿） |
| B-1-2 | 至少一条有材料名的明细 | 负向 | 新增弹窗 | 明细 materialName 留空提交 | warning「请至少填写一条入库明细」 | 无 |
| B-1-3 | 明细金额联动=数量×单价 toFixed(2) | 功能 | 明细行 | 输入 quantity=2.5、unitPrice=100 | 金额列显示 250.00 | 无 |
| B-1-4 | 数量/单价边界 min=0 precision=2 | 边界 | 明细行 | 尝试输入负数/三位小数 | input-number 拒绝负值、截断 2 位 | 无 |
| B-1-5 | 切换项目后清空采购合同 | 功能 | 已选合同 | 更换项目 | contractId 被重置 | 无 |
| B-1-6 | 提交仅 DRAFT 且二次确认 | 功能 | 草稿行 | 点击提交 | 确认框「提交后将更新库存与合同累计入库」，确认后状态变更 | L5-API + E2E expense-write-2.spec.ts（真实模式，2026-08-18 全绿；提交直批 APPROVED + 库存更新断言 stockQuantity/totalInbound=入库量，save 不动库存 submit 才更新实证） |
| B-1-7 | 编辑回显明细 | 功能 | 已有单据 | 点编辑 | 明细数量/单价 Number 回显正确 | 无 |
| B-1-8 | 编辑模式绕过明细必填守卫 | 负向 | 已有单据 | 编辑时删光明细确定 | 源码仅 !isEdit 时校验→可保存空明细（应补守卫） | 无 |
| B-1-9 | 空 materialName 明细行被 payload 过滤 | 边界 | 2 行明细 1 行空名 | 提交 | payload.details 仅含非空行 | 无 |
| B-1-10 | 按项目搜索/重置、分页 page/size | 功能 | 多条数据 | 选项目搜索→翻页→改 pageSize | 列表过滤、page 回到 1；sizes=[10,20,50] | L5-API |
| B-1-11 | 列表字段一致性 | 一致性 | 种子数据 | 对比接口 records | 金额 toLocaleString、directOutbound 1→是、状态映射正确 | L5-一致性 |
| B-1-12 | 状态列仅二分显示 | 一致性 | SUBMITTED 单据 | 查看状态列 | 非 APPROVED 一律显示「草稿」——与调拨页四态不一致，需确认设计 | 无 |

### B2 领料出库（/material/outbound）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-2-1 | 必填 projectId/outboundType | 负向 | 新增弹窗 | 清空后确定 | 必填提示 | 无 |
| B-2-2 | 至少一条出库明细守卫 | 负向 | 新增弹窗 | details 为空确定 | warning「请至少添加一条出库明细」 | 无 |
| B-2-3 | 出库类型 PICK/RETURN 切换与展示 | 功能 | — | 新建退货单 | 列表显示「退货」 | 无 |
| B-2-4 | 出库数量超库存 | 负向 | 库存 10 | 新建 quantity=999 出库单 | 前端不校验（无库存比对）→后端应拦截；前端盲点。**2026-08 代码取证修正**：拦截点在 save 阶段（MaterialOutboundService.save PICK 分支查库存并扣减，submit 仅 DRAFT→APPROVED），超量保存即被拒且不落库 | E2E expense-write-2.spec.ts（真实模式，2026-08-18 全绿） |
| B-2-5 | 提交仅 DRAFT + 确认框 | 功能 | 草稿行 | 提交 | 确认后调 submitMaterialOutbound | L5-API |
| B-2-6 | 编辑回显 | 功能 | 已有单 | 编辑 | outboundType 缺省回落 PICK | 无 |
| B-2-7 | 展开行显示明细子表 | 功能 | 有明细行 | 点展开 | 名称/规格/单位/数量/单价正确 | 无 |
| B-2-8 | 类型筛选+项目筛选搜索/重置 | 功能 | 混合数据 | outboundType=RETURN 搜索 | 仅退货记录；重置恢复 | L5-API + E2E expense-write-2.spec.ts（真实模式，2026-08-18 全绿；PICK/RETURN 筛选参数下发抓包实证） |
| B-2-9 | 删除确认 | 功能 | 草稿行 | 删除 | ElMessageBox 确认后成功 | 无 |
| B-2-10 | 分页参数 pageNum/pageSize | 边界 | 数据>10 | 翻页 | 与入库页 page/size 参数名不一致，接口均正常 | 无 |

### B3 材料调拨（/material/transfer）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-3-1 | 必填 fromProjectId/toProjectId | 负向 | 新增弹窗 | 留空确定 | 必填提示 | 无 |
| B-3-2 | 调出=调入同项目拦截 | 负向 | 新增弹窗 | 选同一项目 | warning「调出项目与调入项目不能相同」 | 无 |
| B-3-3 | 同项目调拨 API 直连绕过前端 | 负向 | — | 直接 POST from=to | 10-material.spec.ts 实测后端接受→后端缺守卫（一致性风险） | L5-API(反向证据) |
| B-3-4 | 至少一条调拨明细守卫 | 负向 | 新增弹窗 | 空明细确定 | warning | 无 |
| B-3-5 | 四态状态标签 | 一致性 | 各状态数据 | 查看列表 | 草稿/审批中/已审批/已驳回 tag 颜色正确 | 无 |
| B-3-6 | 操作按钮状态条件渲染 | 功能 | 各状态行 | 观察按钮 | 编辑/删除仅 DRAFT；提交 DRAFT 或 REJECTED | 无 |
| B-3-7 | 提交后提示「审批通过后变更库存」 | 功能 | 草稿单 | 提交 | 成功文案；库存实际变更见集成 | L5-API(容忍多码) |
| B-3-8 | 调出/调入项目筛选搜索 | 功能 | 多项目数据 | 分别筛选 | 过滤正确 | 无 |
| B-3-9 | 编辑回显明细 | 功能 | 草稿单 | 编辑 | details 原样回显 | 无 |
| B-3-10 | 数量 min=0 precision=2 边界 | 边界 | 明细行 | 输入 0/负数 | 0 允许、负数拒绝 | 无 |
| B-3-11 | 分页与删除确认 | 功能 | 草稿单 | 删除 | 确认后删除成功 | L5-API |

### B4 库存查询（/material/stock）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-4-1 | 库存预警判定 stockQuantity<=minStock | 功能 | 低库存记录 | 查看状态列 | 红色「库存不足」 | 无 |
| B-4-2 | minStock 为 null 时恒为「正常」 | 边界 | minStock=null | 查看 | success 标签（短路） | 无 |
| B-4-3 | warning=LOW/NORMAL 筛选 | 功能 | 混合数据 | 选「不足」搜索 | 后端过滤生效 | 无 |
| B-4-4 | materialName/projectName 模糊搜索 | 功能 | 有数据 | 输入关键字 | 过滤正确，搜索重置 page=1 | L5-API |
| B-4-5 | 只读页面无增删改入口 | 一致性 | — | 检查工具栏 | 无新增按钮（纯查询页） | 无 |
| B-4-6 | 重置清空三条件 | 功能 | 已搜索 | 重置 | 参数归位并加载 | 无 |
| B-4-7 | 分页 pageNum/pageSize | 边界 | 数据>10 | 翻页/改 size | 正确 | 无 |
| B-4-8 | 空结果渲染 | 边界 | 无匹配 | 搜索生僻词 | 空表格不报错 | 无 |

### B5 退货退款（/material/refund）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-5-1 | 只读守卫：无增删改按钮 | 一致性 | — | 检查页面 | 仅 alert 说明+明细按钮 | 无 |
| B-5-2 | 退货出库审批→自动生成退款记录 | 集成 | RETURN 出库单 | 提交审批通过 | 退款列表出现记录 | 无 |
| B-5-3 | 按采购合同 ID 搜索 | 功能 | 有记录 | 输入 contractId | 过滤正确；重置恢复 | L5-API |
| B-5-4 | 明细弹窗字段完整性 | 功能 | 有记录 | 点明细 | 退款单号/金额/合同ID/关联出库单ID/原因+明细表 | 无 |
| B-5-5 | 状态四态映射 | 一致性 | 各状态 | 查看 | 草稿/待审批/已通过/已驳回 | 无 |
| B-5-6 | formatMoney 空值容错 | 边界 | 金额为 null | 渲染 | 显示「-」 | 无 |
| B-5-7 | 退款金额=退货数量×单价 勾稽 | 一致性 | 有退款单 | 对比明细 | 明细 amount 合计=refundAmount（守卫历史「打错表」事故） | 无 |
| B-5-8 | 分页 page/size | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B-X 材料跨模块集成

| 用例ID | 测试点 | 链路 | 预期 |
|---|---|---|---|
| B-M-X1 | 入库审批→采购结算前置 | 入库单 APPROVED → 采购结算新建 | available-inbounds 出现该单；未审批单不出现 |
| B-M-X2 | 入库提交→库存+合同累计入库回写 | 提交入库单 | 库存新增量；采购合同累计入库增加 |
| B-M-X3 | 退货出库审批→退款生成→合同累计冲减 | RETURN 出库提交 | 退款记录生成、outboundId 关联正确 |
| B-M-X4 | 调拨审批→双项目库存联动 | 调拨审批通过 | 调出减、调入增 |
| B-M-X5 | 直接出库开关 directOutbound=1 语义 | 入库时开启直接出库 | 库存净增=0（入即出），列表显示「是」 |

## B-2 机械管理（/machine，8 页）

**业务概述**：合同（含打印 PrintButton business-type=MACHINE）→台账→进出场（台账状态 REGISTERED/IN_FIELD/OUT_FIELD）→工作日志（后端仅允许 IN_FIELD 机械）→结算：create 页按项目+周期拉 usage 明细预览合计，状态为数字 0/1/2/3。

### B6 机械合同（/machine/contract）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-6-1 | 必填 contractName/supplierName/machineName | 负向 | 新增弹窗 | 留空确定 | 三条必填提示 | 无 |
| B-6-2 | 合同金额 min=0 | 边界 | 新增弹窗 | 输入负数 | input-number 拒绝 | 无 |
| B-6-3 | 租赁方式默认月租 | 功能 | 新增 | 查看默认值 | rentalType='月租' | 无 |
| B-6-4 | 合同名称+供应商搜索/重置 | 功能 | 有数据 | 搜索 | 过滤正确 | L5-API |
| B-6-5 | 编辑回显（整行 spread） | 功能 | 已有合同 | 编辑保存 | 更新成功 | L5-API |
| B-6-6 | 打印按钮变量构造 | 功能 | 合同行 | 点打印 | buildPrintVariables 8 字段齐全，空值回落 ''/0 | 无 |
| B-6-7 | 删除确认 | 功能 | 合同行 | 删除 | 确认后成功 | 无 |
| B-6-8 | 页面无提交审批按钮但 API 存在 | 一致性 | — | 对照 api/machine.ts | submitMachineContract 未挂 UI；审批入口缺失需确认设计 | L5-API |
| B-6-9 | 列表字段一致性（8 列无状态列） | 一致性 | 种子数据 | 对比接口 | 与 consistency 断言一致 | L5-一致性 |
| B-6-10 | 分页 pageNum/pageSize | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B7 机械台账（/machine/ledger）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-7-1 | 必填 machineName | 负向 | 新增 | 留空确定 | 「请输入设备名称」 | 无 |
| B-7-2 | 权属 OWN/RENT 展示 | 功能 | 两类设备 | 查看列 | 自有/租赁映射正确 | 无 |
| B-7-3 | 状态三态 | 一致性 | 各状态设备 | 查看 | 在场(success)/已退场(info)/已登记(warning) | 无 |
| B-7-4 | 设备名称+类型搜索/重置 | 功能 | 有数据 | 搜索 | 过滤正确 | L5-API |
| B-7-5 | 新增默认 ownerType=OWN | 边界 | 新增 | 查看默认 | 'OWN' | 无 |
| B-7-6 | 编辑/删除 | 功能 | 设备行 | 编辑保存、删除确认 | 成功 | L5-API |
| B-7-7 | 删除有进出场记录的设备 | 负向 | 设备已进场 | 删除 | 前端直接调 delete，引用守卫依赖后端 | 无 |
| B-7-8 | 分页 | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B8 进出场登记（/machine/entry）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-8-1 | 必填 machineId/projectId/entryType | 负向 | 新增 | 留空确定 | 三条提示 | 无 |
| B-8-2 | entryDate 无必填规则 | 边界 | 新增 | 不选日期确定 | 前端放行→后端兜底待验证 | 无 |
| B-8-3 | 进场后台账状态置 IN_FIELD | 集成 | 设备 REGISTERED | 登记进场 | 台账显示「在场」 | L5-API |
| B-8-4 | 出场后台账置 OUT_FIELD | 集成 | 在场设备 | 登记出场 | 「已退场」；再记工作日志被拒（见 B-9-5） | L5-API |
| B-8-5 | 类型筛选 IN/OUT + tag 颜色 | 功能 | 混合记录 | 筛选 | 进场 success/出场 danger | L5-API |
| B-8-6 | 重复进场 | 负向 | 在场设备 | 再登记进场 | 后端状态机守卫待验证 | 无 |
| B-8-7 | 编辑/删除确认 | 功能 | 记录行 | 操作 | 成功 | 无 |
| B-8-8 | 设备选择器数据源 | 功能 | — | 打开下拉 | 列出台账设备 | 无 |
| B-8-9 | 分页 | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B9 台班/工作量（/machine/work-log）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-9-1 | 必填 machineId/workDate | 负向 | 新增 | 留空确定 | 提示 | 无 |
| B-9-2 | 精度：shiftCount=1 位、workQuantity=2 位、oil=1 位，均 min=0 | 边界 | 新增 | 输入 1.25 台班 | 台班截断 1 位 | 无 |
| B-9-3 | 结算状态展示 | 一致性 | 已结算日志 | 查看 | SETTLED success/未结算 info | 无 |
| B-9-4 | 设备名+工作日期搜索 | 功能 | 有数据 | 搜索 | 过滤正确 | L5-API |
| B-9-5 | 非 IN_FIELD 机械记日志被拒 | 负向 | OUT_FIELD 设备 | 创建日志 | 后端拒绝（须先重新进场） | L5-API(隐式) |
| B-9-6 | 编辑合并默认值 | 功能 | 已有日志 | 编辑 | 缺字段回落 0 | L5-API |
| B-9-7 | 已结算日志的编辑/删除守卫 | 负向 | SETTLED 日志 | 编辑/删除 | 前端无禁用（盲点），依赖后端守卫 | 无 |
| B-9-8 | 台班数为 0 提交 | 边界 | 新增 | shiftCount=0 | 前端放行，语义待确认 | 无 |
| B-9-9 | 分页 | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B10 故障维修（/machine/repair）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-10-1 | 必填 machineId/faultDescription | 负向 | 新增 | 留空确定 | 提示 | 无 |
| B-10-2 | 新增走 /repair/report，默认 REPORTED | 功能 | 新增 | 保存 | report 接口；默认状态已报修 | L5-API |
| B-10-3 | 状态选择器仅编辑态显示 | 功能 | 新增 vs 编辑 | 对比表单 | v-if="isEdit" | 无 |
| B-10-4 | 四态流转展示 | 一致性 | 各状态 | 查看列表 | 已报修/已派工/维修中/已完成 tag | 无 |
| B-10-5 | 维修费用 min=0 | 边界 | 编辑 | 负数 | 拒绝 | 无 |
| B-10-6 | 设备名搜索/重置 | 功能 | 有数据 | 搜索 | 过滤 | L5-API |
| B-10-7 | 派工（→DISPATCHED） | 集成 | 已报修 | 编辑置已派工 | API 层 dispatch 存在（容忍多码） | L5-API |
| B-10-8 | 删除确认 | 功能 | 记录行 | 删除 | 成功 | 无 |
| B-10-9 | 分页 | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B11 机械结算列表（/machine/settlement）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-11-1 | 费用总览三卡片渲染 | 功能 | 有结算数据 | 进入页面 | 已结算/已付款/未付款=summary 值 | L5-API |
| B-11-2 | summary 接口失败不阻断 | 负向 | mock 500 | 进入 | catch 静默，列表仍渲染 | 无 |
| B-11-3 | 状态筛选数字枚举 0/1/2/3 | 功能 | 各状态 | 逐一切换 | 过滤正确 | 无 |
| B-11-4 | 结算周期 dateRange→periodStart/periodEnd | 功能 | 有数据 | 选区间搜索 | 参数组装正确；仅 length===2 才附加 | 无 |
| B-11-5 | 提交审批仅 status 0/3 可见 | 功能 | 各状态行 | 观察 | v-if="row.status === 0 \|\| row.status === 3" | 无 |
| B-11-6 | 提交审批确认框+成功刷新 | 功能 | 草稿单 | 提交 | 确认后 submit，提示「提交成功」 | 无 |
| B-11-7 | 导出 Excel blob 下载 | 功能 | 结算单 | 点导出 | 文件名`机械结算单_{code}.xlsx`，revokeObjectURL | 无 |
| B-11-8 | 导出失败提示 | 负向 | mock 导出 500 | 点导出 | ElMessage.error('导出失败') | 无 |
| B-11-9 | 新建跳转 create 页 | 功能 | — | 点新建 | router.push | 无 |
| B-11-10 | 查看跳转 detail/:id | 功能 | 行 | 点查看 | 正确路由 | 无 |
| B-11-11 | 分页+项目筛选联动 summary | 功能 | 多项目 | 选项目搜索 | loadData+loadSummary 均带 projectId | L5-API |

### B12 新建结算单（/machine/settlement/create）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-12-1 | 必填 projectId/period | 负向 | 空表单 | 保存 | 「请选择项目」「请选择结算周期」 | 无 |
| B-12-2 | 预览仅双条件齐备时加载 | 功能 | 只选项目 | 查看预览区 | previewVisible=false | 无 |
| B-12-3 | 预览金额=工作天数×单价，合计=sum(amount) | 功能 | 有 usage 数据 | 选项目+周期 | computed totalAmount 与明细一致 | 无 |
| B-12-4 | 切换项目/周期自动重载预览 | 功能 | 已预览 | 改周期 | handlePeriodChange→loadPreview | 无 |
| B-12-5 | 预览为空仍可保存 | 负向 | 周期内无台班 | 保存 | 前端无空明细守卫（盲点）→创建 0 元结算单 | 无 |
| B-12-6 | 保存 payload 与跳转 | 功能 | 合法输入 | 保存 | createMachineSettlement 后跳转列表+成功提示 | 无 |
| B-12-7 | 同项目同周期重复结算 | 负向 | 已存在同周期单 | 再次保存 | 前端无重复守卫，依赖后端 | 无 |
| B-12-8 | usage 接口失败降级 | 负向 | mock 失败 | 选条件 | previewData=[]，不阻断 | 无 |
| B-12-9 | 取消/返回 router.back() | 功能 | — | 点取消 | 返回上一页 | 无 |

### B13 结算单详情（/machine/settlement/detail/:id）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-13-1 | 雪花 ID 字符串传递防精度丢失 | 边界 | 长 ID 单据 | 打开详情 | route.params.id 直传字符串（源码注释实证） | 无 |
| B-13-2 | 提交审批按钮仅 status 0/3 | 功能 | 各状态单 | 观察 | v-if 条件 | 无 |
| B-13-3 | 基本信息五项渲染 | 一致性 | 有数据 | 查看 | 编号/周期/金额/状态/流程ID（缺省'-'） | 无 |
| B-13-4 | 明细表渲染 | 一致性 | 有明细 | 查看 | 与创建预览同构 | 无 |
| B-13-5 | 详情页提交后重新加载 | 功能 | 草稿单 | 提交 | loadDetail 刷新状态 | 无 |
| B-13-6 | 导出 Excel | 功能 | 任意单 | 导出 | 同列表页逻辑 | 无 |
| B-13-7 | 无效 id 打开 | 负向 | id 不存在 | 直达路由 | detail={} 不崩溃；无 id 时直接 return | 无 |
| B-13-8 | 数字状态映射与列表页一致 | 一致性 | — | 对比 | 同一 statusMap 0/1/2/3 | 无 |

### B-X 机械跨模块集成

| 用例ID | 测试点 | 链路 | 预期 |
|---|---|---|---|
| B-J-X1 | 台账→进场→工作日志链路 | 新建设备→进场→记台班 | 各步状态衔接，日志创建成功 |
| B-J-X2 | 台班→结算金额聚合 | 周期内日志→create 预览→保存 | 结算金额=Σ(workDays×unitPrice)，与预览合计一致 |
| B-J-X3 | 结算审批→总览回写 | 提交审批通过 | summary.totalSettledAmount 增加，未付款=已结算-已付款 |
| B-J-X4 | 退场后日志拦截 | OUT_FIELD 设备记日志 | 后端拒绝（负向集成） |
| B-J-X5 | 结算审批→项目支出回写 | 审批通过 | 项目 totalExpense 含机械费（预算管控依赖此累计） |

## B-3 劳务管理（/labor，6 页）

**业务概述**：劳务合同（DRAFT/EFFECTIVE）→班组/花名册（在场状态 status===1）→用工单（金额预览 hours×hourlyRate+overtime×overtimeRate，注明「最终以后端计算为准」）→工资单由周期内已审批用工单自动汇总→薪资统计（应发/扣款/实发 + 同比环比）。

### B14 劳务合同（/labor/contract）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-14-1 | 必填 contractName/teamName | 负向 | 新增 | 留空确定 | 提示 | 无 |
| B-14-2 | 金额 min=0 | 边界 | 新增 | 负数 | 拒绝 | 无 |
| B-14-3 | 状态筛选 DRAFT/EFFECTIVE | 功能 | 混合数据 | 筛选 | 过滤正确 | 无 |
| B-14-4 | 状态展示 生效/草稿 | 一致性 | 各状态 | 查看 | EFFECTIVE→生效 success | L5-一致性 |
| B-14-5 | 名称+队伍搜索/重置 | 功能 | 有数据 | 搜索 | 过滤 | L5-API |
| B-14-6 | 编辑/删除 | 功能 | 合同行 | 操作 | 成功+确认框 | L5-API |
| B-14-7 | 页面无提交按钮但 API 存在 | 一致性 | — | 对照 api/labor.ts | 审批入口缺失，与 L5-API「提交劳务合同审批」不对称 | L5-API |
| B-14-8 | 生效合同编辑守卫 | 负向 | EFFECTIVE 合同 | 编辑保存 | 前端无禁用（盲点），后端守卫 | 无 |
| B-14-9 | 分页 | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B15 班组管理（/labor/team）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-15-1 | 必填 teamName/leaderName/workType | 负向 | 新增 | 留空确定 | 三条提示 | 无 |
| B-15-2 | 人数 min=1 默认 1 | 边界 | 新增 | 输入 0 | input-number 拒绝 | 无 |
| B-15-3 | 班组名+工种搜索/重置 | 功能 | 有数据 | 搜索 | 过滤 | 无 |
| B-15-4 | 新增/编辑/删除 CRUD | 功能 | — | 全流程 | 成功+确认 | 无（API 测试无 team 用例） |
| B-15-5 | 联系电话无格式校验 | 边界 | 新增 | 输入非法号码 | 前端放行（无规则，盲点） | 无 |
| B-15-6 | 班组作为工资单下拉源 | 集成 | 有班组 | 打开 payroll 生成弹窗 | teamOptions 含该班组 | 无 |
| B-15-7 | 删除被用工单引用的班组 | 负向 | 班组有用工单 | 删除 | 引用守卫依赖后端 | 无 |
| B-15-8 | 分页 | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B16 劳务花名册（/labor/roster）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-16-1 | 必填 workerName/idCard | 负向 | 新增 | 留空确定 | 提示 | 无 |
| B-16-2 | 身份证号无格式/查重校验 | 负向 | 新增 | 输入 5 位随意字符 | 前端放行（盲点） | 无 |
| B-16-3 | 在场状态 status===1 展示 | 一致性 | 在场/退场 | 查看 | 在场 success/退场 info | 无 |
| B-16-4 | 姓名+班组+工种三条件搜索/重置 | 功能 | 有数据 | 组合搜索 | 过滤 | 无 |
| B-16-5 | 新增/编辑/删除 CRUD | 功能 | — | 全流程 | 成功 | L5-API |
| B-16-6 | 表单无退场日期字段但列表展示 exitDate | 一致性 | 退场人员 | 编辑 | exitDate 只读展示、不可编辑（观察项） | 无 |
| B-16-7 | 进场日期选未来日期 | 边界 | 新增 | 选明天 | 前端无限制 | 无 |
| B-16-8 | 分页 | 边界 | 数据>10 | 翻页 | 正确 | L5-API |

### B17 用工单（/labor/work-order）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-17-1 | 必填 projectId/teamId/workerName/orderType/workDate | 负向 | 新增 | 留空确定 | 五条提示 | 无 |
| B-17-2 | 合计预览=hours×hourlyRate+overtime×overtimeRate | 功能 | 新增 | hours=8,rate=50,overtime=2,otRate=75 | 预览 550.00 元 | 无 |
| B-17-3 | 预览与后端计算一致性 | 一致性 | 同上 | 保存后对比 totalAmount | 列表合计应=预览值（「最终以后端计算为准」核对用例） | 无 |
| B-17-4 | 工时/时薪 min=0 边界 | 边界 | 新增 | 负数/高精度 | precision 1/2 位截断 | 无 |
| B-17-5 | 提交/删除仅 DRAFT 可见 | 功能 | 各状态行 | 观察按钮 | v-if status==='DRAFT' | 无 |
| B-17-6 | 提交确认后状态→已确认 | 功能 | 草稿单 | 提交 | APPROVED 显示「已确认」 | 无（API 测试无用工单用例） |
| B-17-7 | 用工类型 FIXED/TEMPORARY 展示 | 功能 | 两类 | 查看 | 固定/临时 | 无 |
| B-17-8 | 项目+班组+状态组合筛选 | 功能 | 多数据 | 组合搜索 | 过滤 | 无 |
| B-17-9 | 编辑合并默认值 | 功能 | 已有单 | 编辑 | emptyForm+row 合并 | 无 |
| B-17-10 | 工人姓名与花名册无联动校验 | 负向 | — | 输入不存在工人 | 前端自由文本（盲点） | 无 |
| B-17-11 | 分页 page/size | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B18 工资单（/labor/payroll）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-18-1 | 生成必填 teamId/orderType/period | 负向 | 生成弹窗 | 留空生成 | 三条提示 | 无 |
| B-18-2 | 选班组自动带出 projectId | 功能 | 班组有项目 | 选班组 | handleTeamChange 填 team.projectId | 无 |
| B-18-3 | 结算金额自动汇总 alert 语义 | 集成 | 周期内有 APPROVED 用工单 | 生成 | totalSettlement=Σ用工单合计 | L5-API |
| B-18-4 | 周期内无已审批用工单生成 | 负向 | 空周期 | 生成 | 0 元单或后端拒绝待验证 | 无 |
| B-18-5 | 三态状态标签 DRAFT/APPROVED/SETTLED | 一致性 | 各状态 | 查看 | 草稿/已审批/已结算 | 无 |
| B-18-6 | 提交/删除仅 DRAFT | 功能 | 各状态 | 观察 | v-if | L5-API |
| B-18-7 | 已付/未付列联动 | 集成 | 有付款 | 查看 | totalPaid+unpaid=totalSettlement 恒等式 | 无 |
| B-18-8 | 班组名+状态筛选/重置 | 功能 | 多数据 | 搜索 | 过滤 | L5-API |
| B-18-9 | 重复生成同班组同周期 | 负向 | 已存在 | 再生成 | 前端无守卫，依赖后端唯一性 | 无 |
| B-18-10 | 分页 pageNum/pageSize | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B19 薪资统计（/labor/salary-stats）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-19-1 | 未选项目查询拦截 | 负向 | 空条件 | 点查询 | warning「请选择项目」 | 无 |
| B-19-2 | 未选月份查询拦截 | 负向 | 只选项目 | 查询 | warning「请选择月份」 | 无 |
| B-19-3 | 汇总栏 应发/扣款/实发恒等 | 功能 | 有数据 | 查询 | totalActual=totalPayable-totalDeduction | 无 |
| B-19-4 | 同比环比为 null 显示「暂无数据」 | 边界 | 无上月数据 | 查询 | 模板分支 | 无 |
| B-19-5 | 比率正负配色 rate-up/rate-down | 功能 | 有升有降 | 查询 | getRateClass | 无 |
| B-19-6 | Tab ALL/FIXED/TEMPORARY 前端过滤 | 功能 | 两类班组 | 切 Tab | filteredTeamList 按 orderType 过滤 | 无 |
| B-19-7 | 班组名称本地 includes 过滤 | 功能 | 多班组 | 输入关键字 | 本地过滤 | 无 |
| B-19-8 | 工人姓名筛选字段未生效 | 负向 | — | 输入工人姓名查询 | queryParams.workerName 既未传 API 也未本地过滤→**失效控件（源码实证盲点）** | 无 |
| B-19-9 | 展开行懒加载明细 + 明细分页>10 | 功能 | 班组人数>10 | 展开 | getSalaryDetail size=10，_detailTotal>10 显示分页 | 无 |
| B-19-10 | 导出按钮禁用态 !statsData | 功能 | 未查询 | 查看按钮 | disabled | 无 |
| B-19-11 | 导出 fetch+Bearer blob | 功能 | 已查询 | 导出 | 文件名 薪资统计_{month}.xlsx；失败提示「导出失败，请稍后重试」 | 无 |
| B-19-12 | 切项目清空旧数据；重置全清 | 功能 | 已查询 | 换项目/重置 | statsData/compareData/searched 清空 | 无 |

### B-X 劳务跨模块集成

| 用例ID | 测试点 | 链路 | 预期 |
|---|---|---|---|
| B-L-X1 | 班组→花名册→用工单链路 | 建班组→添人员→按班组开单 | teamId 关联正确，TeamSelector 数据一致 |
| B-L-X2 | 用工单 APPROVED→工资单汇总 | 周期内 3 张已确认用工单→生成工资单 | totalSettlement=三单合计 |
| B-L-X3 | 工资单提交→薪资统计可见 | submitPayroll 审批通过 | 当月 stats 出现该班组应发/实发 |
| B-L-X4 | 工资单结算→项目劳务支出回写 | 审批/结算 | 项目 totalExpense 增加，预算管控累计依据 |
| B-L-X5 | 个税/扣款展示链路 | 有扣款数据 | 扣款合计与明细 deduction 之和一致 |

## B-4 分包管理（/subcontract，2 页）

**业务概述**：分包合同（contractName/subcontractor 必填）；结算单以「项目→分包合同→明细行（工程项/数量/单价）」构造，合计实时计算，提交后进入审批，列表展示本次结算与累计结算双金额。

### B20 分包合同（/subcontract/contract）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-20-1 | 必填 contractName/subcontractor | 负向 | 新增 | 留空确定 | 提示 | 无 |
| B-20-2 | 金额 min=0 precision=2 | 边界 | 新增 | 负数 | 拒绝 | 无 |
| B-20-3 | 名称+分包方搜索/重置 | 功能 | 有数据 | 搜索 | 过滤 | L5-API |
| B-20-4 | 状态展示与筛选缺失 | 一致性 | 各状态 | 查看 | 展示正确；查询区无状态筛选（对比劳务/采购有） | L5-一致性 |
| B-20-5 | 编辑/删除 | 功能 | 合同行 | 操作 | 成功+确认 | L5-API |
| B-20-6 | 页面无提交按钮但 API 存在 | 一致性 | — | 对照 api | UI 审批入口缺失 | L5-API |
| B-20-7 | 有结算的合同删除守卫 | 负向 | 合同已结算 | 删除 | 前端直接调 delete，依赖后端引用守卫 | 无 |
| B-20-8 | 分页 | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B21 分包结算（/subcontract/settlement）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-21-1 | 必填 projectId/contractId | 负向 | 新增 | 留空确定 | 提示 | E2E expense-write-2.spec.ts（真实模式，2026-08-18 全绿） |
| B-21-2 | 明细为空守卫 | 负向 | 新增 | 不加明细确定 | warning「请至少添加一条结算明细」 | 无 |
| B-21-3 | 切换项目清空合同选择 | 功能 | 已选合同 | 换项目 | contractId 重置，SubcontractSelector 按 projectId 过滤 | 无 |
| B-21-4 | 小计=数量×单价、合计=Σ小计 联动 | 功能 | 2 行明细 | 输入数值 | 行小计与 totalAmount 实时正确 | E2E expense-write-2.spec.ts（真实模式，2026-08-18 全绿；创建 DRAFT 时联动渲染 + settlementAmount=明细合计断言） |
| B-21-5 | 数量/单价 min=0 边界 | 边界 | 明细行 | 负数 | 拒绝 | 无 |
| B-21-6 | payload 自动注入 sortOrder | 功能 | 多行明细 | 保存 | details.map 注入 i+1 | 无 |
| B-21-7 | 编辑回显明细（Number 转换） | 功能 | 已有单 | 编辑 | quantity/unitPrice Number 回显 | 无 |
| B-21-8 | 提交/删除仅 DRAFT | 功能 | 各状态 | 观察按钮 | v-if | L5-API + E2E expense-write-2.spec.ts（真实模式，2026-08-18 全绿；UI 提交 APPROVED + 详情级联回读明细，VO 嵌套 settlement/details 实证） |
| B-21-9 | 累计结算金额展示与回写 | 集成 | 同合同第二张单 | 审批通过 | cumulativeSettlement=历次之和 | 无 |
| B-21-10 | 结算超合同金额预算管控 | 负向 | 累计>contractAmount | 提交 | 后端 BLOCK/WARN_ONLY 拦截（前端无此校验） | 无 |
| B-21-11 | 合同/状态筛选搜索 | 功能 | 多数据 | 筛选 | 过滤 | L5-API |
| B-21-12 | 分页 page/size | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B-X 分包跨模块集成

| 用例ID | 测试点 | 链路 | 预期 |
|---|---|---|---|
| B-S-X1 | 合同生效→结算可选 | 合同提交生效后新建结算 | SubcontractSelector 列出该合同 |
| B-S-X2 | 结算审批→累计结算+项目支出回写 | 提交审批通过 | cumulativeSettlement 与项目 totalExpense 同步增加 |
| B-S-X3 | 超合同额预算 BLOCK | 累计结算>合同额且预算 BLOCK | 提交被拦截并提示 |
| B-S-X4 | WARN_ONLY 模式放行 | 同上但 WARN_ONLY | 提示警告但允许继续 |

## B-5 采购管理（/purchase，3 页）

**业务概述**：询价单为单物料表单但 payload 组装为 items 数组（后端持久化到 biz_inquiry_item）；发布仅 DRAFT；采购合同三必填含金额；结算强绑定已审批入库单：选合同→拉 available-inbounds→入库金额回填→结算金额不得超过入库金额。

### B22 采购合同（/purchase/contract）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-22-1 | 必填 contractName/supplierName/contractAmount | 负向 | 新增 | 留空确定 | 三条提示 | 无 |
| B-22-2 | 金额 min=0 | 边界 | 新增 | 负数 | 拒绝 | 无 |
| B-22-3 | 名称+供应商+状态筛选/重置 | 功能 | 混合数据 | 组合搜索 | 过滤 | L5-API |
| B-22-4 | 状态 EFFECTIVE/DRAFT 展示 | 一致性 | 各状态 | 查看 | 生效/草稿 | L5-一致性 |
| B-22-5 | 编辑/删除 | 功能 | 合同行 | 操作 | 成功+确认 | L5-API |
| B-22-6 | 非草稿合同编辑被拒 | 负向 | EFFECTIVE 合同 | 直接 PUT | 后端守卫拒绝（08-purchase 实证） | L5-API |
| B-22-7 | 页面无提交按钮但 API 存在 | 一致性 | — | 对照 api | UI 审批入口缺失 | L5-API(容忍多码) |
| B-22-8 | 合同内容 textarea 长文本 | 边界 | 新增 | 500 字 | 保存成功 | 无 |
| B-22-9 | 分页 | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B23 采购结算（/purchase/settlement）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-23-1 | 必填 contractId/inboundId/settlementAmount | 负向 | 新增 | 留空确定 | 三条提示 | 无 |
| B-23-2 | 入库单下拉在未选合同前禁用 | 功能 | 新增 | 查看 | disabled=!formData.contractId | 无 |
| B-23-3 | 选合同加载可结算入库单；空时提示 | 功能 | 合同无已审批入库单 | 选合同 | empty-tip「该合同暂无可结算的已审批入库单」 | 无 |
| B-23-4 | 未审批入库单不在候选中 | 集成 | 合同有 DRAFT 入库单 | 选合同 | available-inbounds 不含该单（「已审批且未结算」语义） | 无 |
| B-23-5 | 选入库单回填入库金额 | 功能 | 有候选 | 选入库单 | inboundAmount=totalAmount | 无 |
| B-23-6 | 结算金额>入库金额拦截（双保险） | 负向 | 入库 10 万 | 输 12 万确定 | input-number max 限制 + warning「结算金额不能大于入库金额」 | 无 |
| B-23-7 | 同一入库单二次结算 | 负向 | 入库单已结算 | 再选合同 | 候选列表不再含该单；API 直连应被拒 | L5-API(间接) |
| B-23-8 | 编辑态合同/入库单锁定 | 功能 | 草稿单 | 编辑 | 两 select disabled=isEdit | 无 |
| B-23-9 | 草稿行三按钮 vs 已审批行文本 | 功能 | 各状态行 | 观察 | 模板分支 | 无 |
| B-23-10 | 提交进入审批确认框 | 功能 | 草稿单 | 提交 | 「确定提交该结算单进入审批流程吗？」→已提交审批 | L5-API |
| B-23-11 | 合同筛选 change 即搜索 | 功能 | 多合同 | 切换下拉 | @change=handleSearch | L5-API |
| B-23-12 | 金额格式化两位小数 | 一致性 | 有数据 | 查看列表 | formatAmount 与接口值一致 | 无 |
| B-23-13 | 分页 page/size | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B24 询价比价（/purchase/inquiry）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| B-24-1 | 必填 title/materialName/quantity | 负向 | 新增 | 留空确定 | 三条提示；materialName 必填即保证 items 非空 | L5-API |
| B-24-2 | 数量 min=1 | 边界 | 新增 | 输入 0 | input-number 拒绝 | 无 |
| B-24-3 | payload 组装 items 数组 | 功能 | 合法表单 | 保存 | buildInquiryPayload 结构 | L5-API |
| B-24-4 | 发布仅 DRAFT 可见 | 功能 | 各状态行 | 观察按钮 | v-if status==='DRAFT' | L5-API |
| B-24-5 | 发布确认框+状态→报价中 | 功能 | 草稿单 | 发布 | PUBLISHED 显示「报价中」 | L5-API |
| B-24-6 | 状态五态展示含 ANNOUNCED | 一致性 | 各状态 | 查看 | 展示分支含 ANNOUNCED，但筛选下拉无此选项（观察项） | 无 |
| B-24-7 | 标题+状态筛选/重置 | 功能 | 多数据 | 搜索 | 过滤 | 无 |
| B-24-8 | 已发布询价编辑守卫 | 负向 | PUBLISHED 单 | 编辑保存 | 前端无禁用（盲点），依赖后端 | 无 |
| B-24-9 | 报价数 quotationCount 展示 | 集成 | 有报价 | 查看 | 与 quotation 接口条数一致（三家报价实证） | L5-API |
| B-24-10 | 删除确认（含已报价单据） | 负向 | QUOTED 单 | 删除 | 前端放行，引用守卫依赖后端 | 无 |
| B-24-11 | 分页 pageNum/pageSize | 边界 | 数据>10 | 翻页 | 正确 | 无 |

### B-X 采购跨模块集成

| 用例ID | 测试点 | 链路 | 预期 |
|---|---|---|---|
| B-P-X1 | 询价→报价→定标→合同 | 发布询价→3 家报价→calculate 排名→confirm 中标 | 最低价排名第一（API 层已覆盖，UI 层未覆盖） |
| B-P-X2 | 入库审批→采购结算前置 | 入库单 APPROVED→结算页选合同 | 候选出现；结算金额=入库金额可保存 |
| B-P-X3 | 结算审批→合同累计结算+项目 totalExpense 回写 | 提交审批通过 | 合同累计增加、项目支出增加、预算管控累计生效 |
| B-P-X4 | 退货出库→退款→采购结算冲减联动 | RETURN 出库审批 | 退款生成（refund 页可见），合同累计口径正确 |
| B-P-X5 | 预算 BLOCK 拦截采购结算 | 累计结算超预算且 BLOCK | 提交被拦截（四类支出合同共性管控） |

---

# 分组 C：财务与现场域（344 例）

## C-1 财务管理（/finance，14 页，159+5 例，覆盖≈20.7% 最低）

**业务概述**：覆盖开票链（申请→收票→汇总）、收款链（回款登记）、付款链（多合同类型付款申请/其他费用付款/项目与个人报销）、资金（备用金借还、质保金计提返还）、结算（跨模块聚合结算单）与管控（封账、税率）。单据统一走 DRAFT→APPROVING/SUBMITTED→APPROVED/REJECTED 状态机（付款另有 PAID），金额经 formatMoney（toLocaleString zh-CN 两位小数、空值 -）格式化，审批提交前有 confirm 确认。

### C1 开票申请（/finance/invoice-apply）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-1-1 | 必填校验 projectId/contractId/invoiceAmount/applyDate | 负向 | 已登录 | 打开新增弹窗，留空必填项点确定 | 各字段显示必填提示，不发请求 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿） |
| C-1-2 | 新增成功：taxRate 走 TaxRateSelector，invoiceType 默认增专 | 功能 | 项目+合同存在 | 完整填写并提交表单 | 创建成功，列表出现新单据，状态草稿 | L5-API + E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；创建 DRAFT + TaxRateSelector UI 选择，税率前提自置 E2E_TEST_ 预设） |
| C-1-3 | 金额边界：invoiceAmount 负值 | 边界 | 弹窗打开 | 输入 -1 | el-input-number min=0 拦截 | 无 |
| C-1-4 | 税率联动：选预设自动填值、手改不一致清选中 | 集成 | 税率数据存在 | 选「增值税13%」→手改为 12 | 填 13.00；手改后清除选中态 | L1 |
| C-1-5 | 提交审批：仅 DRAFT/REJECTED 可提交 | 功能 | 存在草稿单 | 点提交→confirm 确认 | 提示「已提交审批，审批通过后生效」，状态变审批中 | L5-API |
| C-1-6 | 非法状态提交拦截 | 负向 | 存在 APPROVING/APPROVED 单 | 查看操作列 | 提交按钮不渲染 | 无 |
| C-1-7 | 删除仅限 DRAFT | 负向 | 存在已审批单 | 查看操作列 | 无删除按钮 | 无 |
| C-1-8 | 查看详情为 stub | 功能 | 列表有数据 | 点「查看」 | ElMessage.info('查看详情功能开发中') | 无 |
| C-1-9 | 金额列 formatMoney 千分位 2 位、空值 '-' | 一致性 | 列表有数据 | 对比接口 invoiceAmount 与单元格 | 严格一致 | L5-一致性 |
| C-1-10 | statusMap：SUBMITTED 与 APPROVING 均映射「审批中」 | 一致性 | 两种状态各一条 | 查看状态列 | 均显示「审批中」 | L5-一致性 |
| C-1-11 | 项目+状态组合筛选重载 | 功能 | 列表有数据 | 选项目+状态后查询 | 请求携带 projectId+status，列表刷新 | 无 |
| C-1-12 | 分页 total 一致 | 一致性 | 数据>1 页 | 翻页 | pageNum/pageSize 生效，total 与接口一致 | L5-一致性 |
| C-1-13 | 封账期间新增拦截 | 集成 | 当前月已封账 | 新增开票申请并提交 | 后端拒绝，前端展示错误提示而非静默 | 无 |
| C-1-14 | 合同级联：项目变更后合同清空 | 功能 | 弹窗已选项目+合同 | 更换项目 | contractId 清空，ContractSelector 重拉 | 无 |

### C2 收票登记（/finance/invoice-received）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-2-1 | 必填校验 projectId/invoiceAmount/invoiceDate | 负向 | 弹窗打开 | 留空提交 | 必填提示，无请求 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿） |
| C-2-2 | 新增成功（仅新增，无编辑/删除） | 功能 | 项目存在 | 完整填写提交 | 创建成功 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；含无编辑/删除入口断言） |
| C-2-3 | 税率列百分比展示 formatTaxRate | 一致性 | 列表有数据 | 对比接口 taxRate | 显示如 "13%" | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿） |
| C-2-4 | 金额负值拦截 | 边界 | 弹窗打开 | 输入负数 | min=0 拦截 | 无 |
| C-2-5 | 列表金额 formatMoney + 分页 | 一致性 | 列表有数据 | 核对金额列 | 千分位 2 位小数 | 无 |
| C-2-6 | 重复提交防抖 | 负向 | 弹窗已填写 | 快速双击确定 | 仅一次请求（button loading） | 无 |
| C-2-7 | 接口失败错误提示 | 负向 | mock 接口 500 | 提交 | ElMessage.error，弹窗保留 | 无 |
| C-2-8 | 无编辑/删除入口 | 负向 | 列表有数据 | 检查操作列 | 无编辑/删除按钮 | 无 |
| C-2-9 | 收票数据计入发票汇总已收票口径 | 集成 | 已登记一笔收票 | 打开发票汇总 | received 口径含该笔 | 无 |

### C3 发票汇总（/finance/invoice-summary）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-3-1 | 日期范围筛选 startDate/endDate | 功能 | 有跨月数据 | 选起止日期查询 | 请求携带日期范围，结果正确过滤 | 无 |
| C-3-2 | show-summary 合计：numberProps reduce 求和 | 一致性 | 列表≥2 行 | 对比合计行 invoicedCount 与明细求和 | 严格相等 | 无 |
| C-3-3 | 合计行金额列 moneyProps formatMoney | 一致性 | 合计非 0 | 对比合计金额 | 千分位 2 位小数 | 无 |
| C-3-4 | 已开票金额/税额口径 | 一致性 | 有已开票数据 | 对比 invoicedAmount/TaxAmount 与开票申请 APPROVED 数据 | 口径一致 | 无 |
| C-3-5 | 已收票口径与收票登记一致 | 集成 | 有收票记录 | 对比 received 系列字段 | 与收票登记汇总一致 | 无 |
| C-3-6 | 空值显示 '-' / 0 显示 0.00 | 边界 | 某项目无开票 | 查看该项目行 | 空→'-'，0→0.00 | 无 |
| C-3-7 | 无分页一次性加载 | 边界 | 数据较多 | 加载页面 | 无分页组件，全量渲染 | 无 |
| C-3-8 | 接口失败提示 | 负向 | mock 失败 | 加载 | ElMessage.error，无数据假象 | 无 |

### C4 回款登记（/finance/payment-received）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-4-1 | 必填校验 projectId/receiveAmount/receiveDate | 负向 | 弹窗打开 | 留空提交 | 必填提示 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿） |
| C-4-2 | 新增成功，receiveType 默认银行转账 | 功能 | 项目存在 | 仅填必填项提交 | 创建成功，receiveType=银行转账 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿） |
| C-4-3 | 编辑回填 {...row} 并保存 | 功能 | 列表有数据 | 点编辑→改金额→保存 | 表单完整回填，update 成功 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；编辑改金额闭环） |
| C-4-4 | 删除走 confirm 二次确认 | 功能 | 列表有数据 | 点删除→确认 | 删除成功，列表刷新 | 无 |
| C-4-5 | receiveType 四枚举 | 功能 | 弹窗打开 | 切换选项 | 银行转账/支票/现金/承兑汇票均可选并保存 | 无 |
| C-4-6 | 金额负值/0 边界 | 边界 | 弹窗打开 | 输入 -1 / 0 | 负值拦截；0 行为与产品口径一致 | 无 |
| C-4-7 | 金额 formatMoney + 分页 total | 一致性 | 列表有数据 | 核对列表 | 格式与 total 一致 | L5-API |
| C-4-8 | 封账期间新增/编辑拦截 | 集成 | 回款日期所在月已封账 | 新增提交 | 后端拒绝并前端提示 | 无 |
| C-4-9 | 重复提交防抖 | 负向 | 表单已填 | 双击提交 | 仅一次请求 | 无 |
| C-4-10 | 回款计入看板「已收款」与项目 totalIncome | 集成 | 登记一笔回款 | 查看 dashboard/项目看板 | 已收款增加该笔 | 无 |

### C5 付款申请（/finance/payment-apply）★核心页

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-5-1 | 必填校验（项目/合同类型/合同/金额/日期） | 负向 | 弹窗打开 | 留空提交 | 必填提示，无请求 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿） |
| C-5-2 | 合同路由：PURCHASE→getPurchaseContractPage | 功能 | 存在采购合同 | 选合同类型=采购 | 合同下拉加载采购合同 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；抓包实证 getPurchaseContractPage） |
| C-5-3 | 合同路由：LABOR→getLaborContractPage | 功能 | 存在劳务合同 | 选劳务 | 加载劳务合同 | 无 |
| C-5-4 | 合同路由：MACHINE→getMachineContractPage | 功能 | 存在机械合同 | 选机械 | 加载机械合同 | 无 |
| C-5-5 | 合同路由：SUBCONTRACT→getSubcontractPage | 功能 | 存在分包合同 | 选分包 | 加载分包合同 | 无 |
| C-5-6 | 合同路由：OTHER_EXPENSE→getOtherContractPage | 功能 | 存在其他支出合同 | 选其他支出 | 加载其他支出合同 | 无 |
| C-5-7 | 项目/类型变更清空 contractId+contractOptions | 边界 | 已选类型与合同 | 更换项目或合同类型 | contractId 清空、下拉重载 | 无 |
| C-5-8 | SupplierSelector @change 回填 supplierName | 功能 | 弹窗打开 | 选择供应商 | supplierName 自动回填 | 无 |
| C-5-9 | 提交审批仅 DRAFT/REJECTED，confirm 提示 | 功能 | 草稿单存在 | 提交 | 状态→审批中，成功提示 | L5-API + E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；完整创建 DRAFT→UI 提交→直批 APPROVED + 状态标签渲染） |
| C-5-10 | statusMap 含 PAID（已付款/primary） | 一致性 | 存在已付款单 | 查看状态列 | 显示「已付款」 primary 标签 | 无 |
| C-5-11 | 查看详情 stub | 功能 | 列表有数据 | 点查看 | ElMessage.info('查看详情功能开发中') | 无 |
| C-5-12 | 金额列 formatMoney + 分页 total | 一致性 | 列表有数据 | 核对 | 严格一致 | L5-一致性 |
| C-5-13 | 审批通过→合同累计付款回写→项目 totalExpense（已付口径） | 集成 | 草稿单提交并审批通过 | 查合同累计付款/项目 totalExpense | 两者均增加付款金额 | 无 |
| C-5-14 | 重复提交防抖 | 负向 | 表单已填 | 双击提交 | 仅一次请求 | 无 |
| C-5-15 | 封账期间拦截 | 集成 | 付款日期所在月已封账 | 提交 | 拒绝并提示 | 无 |
| C-5-16 | 非法状态操作隐藏 | 负向 | 各状态单据存在 | 检查操作列 | 审批中/已付款不可编辑删除 | 无 |

### C6 其他费用付款（/finance/other-payment）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-6-1 | 分页参数为 page/size（异于其他财务页） | 一致性 | 列表有数据 | 抓包翻页请求 | 参数名 page/size 正确、翻页生效。**2026-08 E2E 实证**：payment-apply 前端传 pageNum/pageSize 但后端 Controller 仅收 page/size，UI 层列表恒 size=10 首页（现状钉住） | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；抓包实证） |
| C-6-2 | 必填校验 projectId/payerName/paymentAmount/paymentDate | 负向 | 弹窗打开 | 留空提交 | 必填提示 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿） |
| C-6-3 | 仅新增：无编辑/删除入口 | 负向 | 列表有数据 | 检查操作列 | 无编辑/删除按钮 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；含完整创建闭环） |
| C-6-4 | statusMap 仅 DRAFT/APPROVED，其他状态兜底 | 边界 | 存在 SUBMITTED 单 | 查看状态列 | 不渲染 undefined/空白崩溃 | 无 |
| C-6-5 | 金额负值拦截 | 边界 | 弹窗打开 | 输入负数 | min=0 拦截 | 无 |
| C-6-6 | 提交审批（仅 DRAFT） | 功能 | 草稿存在 | 提交 | 状态流转成功 | 无 |
| C-6-7 | 金额 formatMoney | 一致性 | 列表有数据 | 核对 | 千分位 2 位小数 | 无 |
| C-6-8 | 重复提交防抖 | 负向 | 表单已填 | 双击提交 | 仅一次请求 | 无 |
| C-6-9 | 接口失败错误提示 | 负向 | mock 500 | 提交 | ElMessage.error | 无 |

### C7 项目报销（/finance/project-reimbursement）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-7-1 | offsetReserve 开关（active=1）控制 offsetAmount 列显隐 | 功能 | 列表有冲销数据 | 切换抵扣开关 | offsetAmount 列与表单项随开关显隐 | 无 |
| C-7-2 | 必填校验 projectId/totalAmount/reimbursementDate | 负向 | 弹窗打开 | 留空提交 | 必填提示 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；空态确定不发 POST。**API-GAP-fin**：后端无 DELETE 通道，不真实建单） |
| C-7-3 | 新增/编辑/删除 CRUD | 功能 | 项目存在 | 依次操作 | 各操作成功且列表刷新 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿。**2026-08 实测修正**：UI 无编辑/删除入口，仅提交按钮且只在草稿行渲染） |
| C-7-4 | 提交仅 DRAFT（submit POST） | 功能 | 草稿存在 | 提交 | 状态→审批中 | 无 |
| C-7-5 | 勾选冲销时 offsetAmount 必填 | 负向 | 开关开启 | 留空冲销额提交 | 校验提示 | 无 |
| C-7-6 | offsetAmount 边界（0/负值/超总额） | 边界 | 开关开启 | 输入 0、-5、大于 totalAmount | 按校验规则拦截或正确计算实付 | 无 |
| C-7-7 | 金额 formatMoney + 分页 | 一致性 | 列表有数据 | 核对 | 一致 | 无 |
| C-7-8 | 重复提交防抖 | 负向 | 表单已填 | 双击 | 一次请求 | 无 |
| C-7-9 | 非 DRAFT 状态不可提交/删除 | 负向 | APPROVED 单存在 | 检查操作列 | 按钮隐藏 | 无 |
| C-7-10 | 冲销联动备用金余额 | 集成 | 存在已借支备用金 | 报销勾选冲销并审批通过 | 备用金待冲销余额相应减少 | 无 |

### C8 备用金管理（/finance/reserve-fund）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-8-1 | 借支申请必填校验 | 负向 | 弹窗打开 | 留空提交 | 必填提示 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿。**API-GAP-fin**：后端无 DELETE 通道，不真实建单） |
| C-8-2 | 借支申请新增成功 | 功能 | 项目/人员存在 | 完整填写提交 | 创建草稿单 | 无 |
| C-8-3 | 归还弹窗仅 APPROVED 行可见 | 负向 | 各状态记录存在 | 检查操作列 | 仅 APPROVED 显示归还 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；逐行状态条件渲染断言，无 APPROVED 行时显式 skip 登记） |
| C-8-4 | 归还必填 returnAmount/returnDate | 负向 | 归还弹窗打开 | 留空提交 | 必填提示 | 无 |
| C-8-5 | 归还调用 createReserveFundReturn | 功能 | APPROVED 记录 | 填归还额提交 | 归还成功，记录状态/余额更新 | 无 |
| C-8-6 | 归还金额>借支金额边界 | 边界 | APPROVED 记录 | 输入超额 | 拦截或提示 | 无 |
| C-8-7 | 提交借支审批（仅 DRAFT） | 功能 | 草稿存在 | 提交 | 状态流转 | 无 |
| C-8-8 | 状态枚举渲染 | 一致性 | 多状态数据 | 核对状态列 | 与 statusMap 一致 | 无 |
| C-8-9 | 金额 formatMoney + 分页 | 一致性 | 列表有数据 | 核对 | 一致 | 无 |
| C-8-10 | 重复提交防抖 | 负向 | 表单已填 | 双击 | 一次请求 | 无 |
| C-8-11 | 归还后余额一致性 | 一致性 | 归还成功 | 对比余额=借支-累计归还 | 严格相等 | 无 |
| C-8-12 | 封账期间借支新增拦截 | 集成 | 当月已封账 | 新增提交 | 拒绝并提示 | 无 |

### C9 个人报销（/finance/personal-reimbursement）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-9-1 | 无项目筛选条件 | 负向 | 打开页面 | 检查筛选区/抓包 | 请求无 projectId 参数 | 受阻（API-GAP-fin：抓包依赖真实建单，后端无 DELETE 通道；tasks.md 登记）。UI 层弹窗无「项目」字段已由 E2E 钉住 |
| C-9-2 | 必填校验 totalAmount/reimbursementDate | 负向 | 弹窗打开 | 留空提交 | 必填提示 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；空态确定不发 POST + 弹窗无项目字段断言） |
| C-9-3 | 仅新增+提交：无编辑/删除 | 负向 | 列表有数据 | 检查操作列 | 无编辑/删除按钮 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；现状钉住） |
| C-9-4 | 提交仅 DRAFT | 功能 | 草稿存在 | 提交 | 状态流转成功 | 无 |
| C-9-5 | 金额负值拦截 | 边界 | 弹窗打开 | 输入 -1 | min 拦截 | 无 |
| C-9-6 | 金额 formatMoney + 分页 | 一致性 | 列表有数据 | 核对 | 一致 | 无 |
| C-9-7 | 重复提交防抖 | 负向 | 表单已填 | 双击 | 一次请求 | 无 |
| C-9-8 | 接口失败提示 | 负向 | mock 500 | 提交 | ElMessage.error | 无 |

### C10 质保金管理（/finance/retention）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-10-1 | 到期预警 alert「N 笔质保金将在 30 天内到期」 | 功能 | getExpiringRetention(30) 非空 | 打开页面 | 顶部 el-alert 显示且 N 正确 | L5-API |
| C-10-2 | 无到期数据不显示 alert | 边界 | 到期列表为空 | 打开页面 | 无 alert | 无 |
| C-10-3 | 计提新增必填校验 | 负向 | 弹窗打开 | 留空提交 | 必填提示 | L5-API |
| C-10-4 | retentionRate 边界 min=0 max=100 | 边界 | 弹窗打开 | 输入 -1 / 101 | 输入被裁剪/拦截 | 无 |
| C-10-5 | 返还按钮 v-if="row.status !== 'RETURNED'" | 负向 | RETURNED 记录存在 | 检查操作列 | 无返还按钮 | 无 |
| C-10-6 | 返还 confirm→审批（退款写表守卫回归） | 功能 | ACTIVE 记录 | 点返还→确认→审批通过 | 返还成功，退款落正确表（历史缺陷回归） | 无 |
| C-10-7 | statusMap ACTIVE/EXPIRED/RETURNED 渲染 | 一致性 | 三态数据 | 核对状态列 | 文案一致 | 无 |
| C-10-8 | 金额 formatMoney + 分页 | 一致性 | 列表有数据 | 核对 | 一致 | L5-API |
| C-10-9 | 重复返还防抖 | 负向 | 返还弹窗 | 双击确认 | 一次请求 | 无 |
| C-10-10 | 预警 N 与到期明细一致 | 一致性 | 有到期数据 | 对比 alert N 与 expiringList.length | 相等 | 无 |
| C-10-11 | 返还后状态与列表刷新 | 功能 | 返还审批通过 | 刷新列表 | 状态 RETURNED | 无 |
| C-10-12 | expiring 接口失败不阻塞列表 | 负向 | mock expiring 500 | 打开页面 | 列表正常加载，无 alert | 无 |

### C11 项目最终结算-列表（/finance/settlement）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-11-1 | 创建结算：选项目→createSettlement→跳转详情携带 id | 功能 | 项目存在且未结算 | 点创建→选项目确认 | 创建成功并跳转 /finance/settlement/{id} | L5-API |
| C-11-2 | profit<0 加 text-danger | 一致性 | 存在亏损结算 | 查看利润列 | 红色样式且数值正确 | 无 |
| C-11-3 | profitRate<0 加 text-danger | 一致性 | 存在负利润率 | 查看利润率列 | 红色样式 | 无 |
| C-11-4 | 导出 Blob 下载 `结算报告_${code}.xlsx` | 功能 | 存在已结算单 | 点导出 | 触发 Blob 下载且文件名正确 | 无 |
| C-11-5 | 导出失败错误提示 | 负向 | mock 导出失败 | 点导出 | ElMessage.error，无假下载 | 无 |
| C-11-6 | 状态枚举 DRAFT/SUBMITTED/APPROVED/REJECTED | 一致性 | 四态数据 | 核对状态列 | 文案一致 | 无 |
| C-11-7 | 金额 formatMoney + 分页 | 一致性 | 列表有数据 | 核对 | 一致 | L5-API |
| C-11-8 | 创建接口失败不跳转并提示 | 负向 | mock 创建失败 | 选项目确认 | 停留列表并报错 | 无 |
| C-11-9 | 行点击/详情入口跳转 | 功能 | 列表有数据 | 进入详情 | 正确携带 id 跳转 | 无 |
| C-11-10 | 未结清合同入口（getUnsettledContracts） | 功能 | 存在未结清合同 | 查看未结清提示 | 展示未结清合同列表 | L5-API |
| C-11-11 | 结算聚合与各支出模块数据一致 | 集成 | 项目有付款/报销/分包支出 | 创建结算查看支出汇总 | 汇总=各支出模块已发生额之和 | 无 |
| C-11-12 | 筛选与翻页 | 功能 | 数据>1 页 | 按状态筛选+翻页 | 请求参数正确 | 无 |

### C12 结算单详情（/finance/settlement/:id）★核心页

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-12-1 | 三卡片渲染：收入汇总/支出汇总/利润分析 | 功能 | 结算单存在 | 打开详情 | 三卡片数据与 getSettlement 一致 | L5-API |
| C-12-2 | 利润正负变色 | 一致性 | 盈利/亏损各一单 | 查看利润 | 正值常色/负值红色 | 无 |
| C-12-3 | 合同明细 unsettledAmount>0 标「未结清」+行高亮 | 一致性 | 含未结清合同 | 查看合同明细 | 标签+高亮正确 | L5-API |
| C-12-4 | unsettledCount computed 与明细一致 | 一致性 | 同上 | 对比计数 | 相等 | 无 |
| C-12-5 | 编辑仅 DRAFT/REJECTED | 负向 | SUBMITTED 单 | 打开详情 | 无编辑入口 | 无 |
| C-12-6 | finalSettlementAmount 留空取累计产值 | 边界 | DRAFT 单编辑 | 清空该字段保存 | 保存值=累计产值 | 无 |
| C-12-7 | otherExpense 录入参与利润计算 | 功能 | 编辑态 | 填其他费用保存 | 利润=收入-支出-其他费用 | 无 |
| C-12-8 | resummarize 开关触发重新汇总 | 功能 | 编辑态 | 开启重新汇总保存 | 各汇总按最新支出重算 | 无 |
| C-12-9 | 提交 confirm 文案「提交后不可修改」「审批通过后项目方可结项」 | 功能 | DRAFT 单 | 点提交 | confirm 文案正确，确认后 submitSettlement | 无 |
| C-12-10 | 非法状态提交拦截 | 负向 | APPROVED 单 | 打开详情 | 无提交按钮 | 无 |
| C-12-11 | 雪花 ID 以字符串传递 | 边界 | 长 ID 结算单 | 列表→详情→刷新 | 详情加载成功，ID 不截断 | 无 |
| C-12-12 | 详情加载失败提示 | 负向 | mock 失败 | 打开 | ElMessage.error | 无 |
| C-12-13 | 支出汇总与付款/报销/其他付款模块一致 | 集成 | 项目有多类支出 | 对比三卡片与源模块合计 | 严格一致 | 无 |
| C-12-14 | 审批通过后项目可结项联动 | 集成 | 结算审批通过 | 查看项目状态入口 | 项目进入可结项状态 | 无 |

### C13 财务封账（/finance/finance-lock）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-13-1 | canOperate：*:*:* 或 FINANCE_ADMIN/ADMIN 显示新增按钮 | 权限 | 财务管理员登录 | 打开页面 | 新增封账按钮+操作列可见。**2026-08 契约实证**：FINANCE_STAFF（wangqiang）不在 OPERATE_ROLES，按钮同样隐藏 | E2E permission.spec.ts（真实模式 wangqiang 视角，2026-08-18 全绿） |
| C-13-2 | 普通用户隐藏新增与操作列 | 权限 | 普通角色登录 | 打开页面 | 按钮与操作列均隐藏（`:text-is` 精确匹配，排除「操作人/操作时间」列误伤） | E2E permission.spec.ts（真实模式 lina/STAFF 视角，2026-08-18 全绿） |
| C-13-3 | period month-picker 格式 YYYY-MM | 功能 | 弹窗打开 | 选 2026-08 | 提交参数 period='2026-08' | 无 |
| C-13-4 | lockType MONTHLY/QUARTERLY | 功能 | 弹窗打开 | 切换类型提交 | 类型正确保存 | 无 |
| C-13-5 | 新增封账成功→状态 LOCKED（已封账/danger） | 功能 | 未封账月份 | 新增封账 | 列表出现已封账红标签 | 无 |
| C-13-6 | 解封 confirm「解封后该期间将允许财务单据操作」 | 功能 | LOCKED 记录 | 点解封→确认 | unlockPeriod 成功，状态 UNLOCKED | 无 |
| C-13-7 | 解封仅特权角色可操作 | 权限 | 普通角色 | 检查操作列 | 无解封按钮 | 无 |
| C-13-8 | 同期间重复封账 | 边界 | 2026-08 已封 | 再次封 2026-08 | 拒绝或提示已存在 | 无 |
| C-13-9 | 分页与筛选 | 功能 | 数据>1 页 | 翻页 | 生效 | L5-API(仅查询) |
| C-13-10 | 封账期间新增财务单据拦截（跨页） | 集成 | 当月已封账 | 去开票/付款页新增 | 后端拒绝+前端可见错误提示 | 无 |
| C-13-11 | 封账期间解封后恢复新增 | 集成 | 解封后 | 再新增单据 | 成功 | 无 |
| C-13-12 | 单据页无封账状态前置检查（getLockStatus 未被 view 使用） | 负向 | 当月已封账 | 打开开票页新增弹窗 | 弹窗可打开，仅在提交时被后端拒绝——前置提示缺失 | 无 |

### C14 税率管理（/finance/tax-rate）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-14-1 | validateRateValue 范围 0.01–99.99 | 负向 | 弹窗打开 | 输入 0 | 校验失败 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；越界输入 el-input-number UI 钳制实证） |
| C-14-2 | 小数位正则：3 位小数拒绝 | 边界 | 弹窗打开 | 输入 13.456 | 校验失败 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；超精度收敛 precision=2） |
| C-14-3 | 边界值 99.99 通过、100 拒绝 | 边界 | 弹窗打开 | 分别输入 | 99.99 通过/100 拒绝 | E2E finance-write.spec.ts（真实模式，2026-08-18 全绿；99.99 创建→列表可见→停用闭环） |
| C-14-4 | name 最大 30 字符 | 边界 | 弹窗打开 | 输入 31 字 | 校验失败 | 无 |
| C-14-5 | 新增/编辑成功 | 功能 | 特权角色 | 填写合法值提交 | 保存成功，列表刷新 | 无 |
| C-14-6 | 停用 confirm「停用后将不可用于新单据」 | 功能 | 启用中税率 | 点停用→确认 | 文案正确，状态变停用 | 无 |
| C-14-7 | deleteTaxRate 为逻辑停用：getAllTaxRates 仍含停用项 | 一致性 | 已停用税率 | 查列表 | 停用项可见带停用标识 | 无 |
| C-14-8 | TaxRateSelector 仅展示启用税率 | 集成 | 存在停用税率 | 开票弹窗打开选择器 | 停用项不出现 | 无 |
| C-14-9 | 选择器预设填值与选项文案「名称（数值%）」 | 功能 | 选择器可用 | 选预设 | 填值正确、文案格式正确 | L1 |
| C-14-10 | 手动输入规范化 2 位小数 | 功能 | 选择器可用 | 输入 3.456 | 规范化 3.46 | L1 |
| C-14-11 | 列表分页与启用状态列 | 一致性 | 列表有数据 | 核对 | 一致 | 无 |
| C-14-12 | 重复税率值新增 | 边界 | 已存在 13% | 再建 13% | 拒绝或允许（以实际为准）需明确 | 无 |

### C-FIN-X 财务跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| C-FIN-X1 | 付款审批通过→合同累计付款回写→项目 totalExpense（已付口径） | 集成 | 四类合同各发起一笔付款并审批通过 | 对应合同 paidAmount 与项目 totalExpense 均增加 | 无 |
| C-FIN-X2 | 开票申请审批通过→发票汇总已开票口径→开票计入结算收入 | 集成 | 开票审批通过后查汇总与结算详情 | 三处口径一致增加 | 无 |
| C-FIN-X3 | 回款登记→项目 totalIncome→看板已收款 | 集成 | 登记回款后查项目与 dashboard | 两处同步增加 | 无 |
| C-FIN-X4 | 封账期间对 5 类单据新增的统一拦截 | 集成 | 封当月后逐页新增提交 | 全部被拒绝且均有可见提示 | 无 |
| C-FIN-X5 | 结算单聚合：支出汇总=付款+项目报销+个人报销+其他付款+分包/机械/采购支出 | 集成 | 项目多源支出后创建结算并 resummarize | 汇总与各源模块合计严格一致 | 无 |

## C-2 现场管理（/site，5 页，56+4 例）

**业务概述**：进度计划以表格+dhtmlx-gantt 甘特图双视图呈现（watch(ganttProjectId) 触发 getSchedulePlanTree，空数据 el-empty）；施工日志按单日 logDate 转 startDate/endDate 查询；质量安全检查由 el-tabs 切换 QUALITY/SAFETY，表单页采用检查方案驱动（选方案→getSchemeItems/applyScheme 填充检查项，快照恢复），整改走 PENDING→SUBMITTED→APPROVED/REJECTED 闭环。

### C15 进度计划（/site/schedule）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-15-1 | progress 列 el-progress 渲染 50% | 一致性 | 列表有数据 | 对比接口 progress | 进度条与文本一致 | L5-一致性 |
| C-15-2 | taskStatus 翻译：COMPLETED/DELAYED/其他 | 一致性 | 三态数据 | 核对状态列 | 已完成/滞后/进行中 | L5-一致性 |
| C-15-3 | 必填校验 taskName/planStartDate/planEndDate | 负向 | 弹窗打开 | 留空提交 | 必填提示 | 无 |
| C-15-4 | progress slider max=100 边界 | 边界 | 弹窗打开 | 拖到最大/输入 101 | 上限 100 | 无 |
| C-15-5 | 新增/编辑/删除 CRUD | 功能 | 项目存在 | 依次操作 | 成功且刷新 | L5-API |
| C-15-6 | 甘特图加载：选项目→getSchedulePlanTree→GanttChart 渲染 | 功能 | 项目有计划数据 | 选择项目 | 甘特图渲染任务条 | L5-API(树接口) |
| C-15-7 | 甘特图空数据 el-empty | 边界 | 项目无计划 | 选择项目 | 显示空状态组件，ganttHasData=false | 无 |
| C-15-8 | 甘特图 editable @task-updated→loadData 回刷 | 集成 | 甘特图有任务 | 拖动任务条 | 触发更新 API 并刷新表格 | 无 |
| C-15-9 | 树接口失败提示 | 负向 | mock tree 500 | 选项目 | 错误提示，甘特区空状态 | 无 |
| C-15-10 | 分页与项目筛选 | 功能 | 多项目数据 | 筛选+翻页 | 参数正确 | L5-API |
| C-15-11 | 甘特图与表格数据一致 | 一致性 | 有计划数据 | 对比两侧任务数 | 一致 | 无 |
| C-15-12 | 重复提交防抖 | 负向 | 表单已填 | 双击保存 | 一次请求 | 无 |

### C16 施工日志（/site/construction-log）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-16-1 | 单日 logDate 查询转 startDate/endDate | 功能 | 列表有数据 | 选单日查询抓包 | 请求携带 startDate=endDate=logDate | 无 |
| C-16-2 | 必填校验 projectId/logDate/productionRecord | 负向 | 弹窗打开 | 留空提交 | 必填提示 | 无 |
| C-16-3 | workerCount min=0 负值拦截 | 边界 | 弹窗打开 | 输入 -1 | 拦截 | 无 |
| C-16-4 | 新增/编辑/删除 CRUD | 功能 | 项目存在 | 依次操作 | 成功 | L5-API |
| C-16-5 | 列表字段（天气/气温/风力/人数/生产记录）一致性 | 一致性 | 列表有数据 | 核对 7 列 | 严格一致 | L5-一致性 |
| C-16-6 | 日期范围筛选 | 功能 | 跨日数据 | 选范围查询 | 正确过滤 | L5-API |
| C-16-7 | 分页 total 一致 | 一致性 | 数据>1 页 | 翻页 | total 一致 | L5-一致性 |
| C-16-8 | 重复提交防抖 | 负向 | 表单已填 | 双击 | 一次请求 | 无 |
| C-16-9 | 接口失败提示 | 负向 | mock 500 | 提交 | ElMessage.error | 无 |
| C-16-10 | 同项目同日重复日志 | 边界 | 当日已有日志 | 再建同日 | 允许/拒绝行为符合产品口径 | 无 |

### C17 检查列表（/site/inspection）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-17-1 | el-tabs quality/safety @tab-change 重载 | 功能 | 两类数据存在 | 切换 tab | 列表按类型重载 | L5-API |
| C-17-2 | 质量 tab 列表与分页 | 功能 | 质量数据存在 | 查看+翻页 | 正确 | L5-一致性 |
| C-17-3 | 安全 tab 列表 | 功能 | 安全数据存在 | 切换查看 | 正确 | L5-API |
| C-17-4 | hasProblem 0→无问题 / 1→有问题 | 一致性 | 两种数据 | 核对列 | 文案一致 | L5-一致性 |
| C-17-5 | RECT_MAP 整改状态四态+空值 '-' | 一致性 | 各状态数据 | 核对整改状态列 | PENDING/SUBMITTED/APPROVED/REJECTED/空 渲染正确 | L5-一致性 |
| C-17-6 | 新增跳转 InspectionCreate | 功能 | 任一 tab | 点新增 | 路由至 /site/inspection/form | 无 |
| C-17-7 | 编辑跳转 InspectionEdit/:id | 功能 | 列表有数据 | 点编辑 | 路由携带 id | 无 |
| C-17-8 | 详情跳转 InspectionDetail/:id | 功能 | 列表有数据 | 点详情 | 路由携带 id | 无 |
| C-17-9 | 删除 confirm + getDeleteApi 按 tab 类型 | 功能 | 列表有数据 | 删除→确认 | 按当前 tab 类型调用删除，列表刷新 | 无 |
| C-17-10 | tab 切换后筛选条件行为 | 边界 | 质量 tab 已设筛选 | 切到安全 | 筛选重置或保留符合实现 | 无 |
| C-17-11 | 整改闭环入口：指派/提交/审批整改 | 集成 | hasProblem=1 记录 | 走 assignRectification→submit→approve | 状态 PENDING→SUBMITTED→APPROVED | L5-API(指派) |
| C-17-12 | 列表接口失败提示 | 负向 | mock 失败 | 加载 | ElMessage.error | 无 |

### C18 检查表单（/site/inspection/form）★核心页

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-18-1 | 基本信息必填校验 | 负向 | 新增模式 | 留空提交 | 必填提示 | 无 |
| C-18-2 | inspectionType 切换清空 schemeId+detailList 并重载方案列表 | 功能 | 已选方案 | 切换质量→安全 | 方案与明细清空，listInspectionSchemes 按类型重载 | L5-API(方案列表) |
| C-18-3 | 新增模式选方案→getSchemeItems 填充 checkResult='NOT_CHECKED' | 功能 | 方案含检查项 | 选择方案 | 明细填充模板项，结果均为未检查 | 无 |
| C-18-4 | 编辑模式选方案→applyScheme+重载 | 功能 | 编辑已有检查 | 更换方案 | applyScheme 生效，明细重载 | 无 |
| C-18-5 | 有方案时 itemName/checkMethod 只读 | 负向 | 已应用方案 | 尝试编辑项名 | 输入框只读 | 无 |
| C-18-6 | 有方案时不可新增方案外检查项 | 负向 | 已应用方案 | 点添加行 | 按钮禁用/不响应 | 无 |
| C-18-7 | 手动添加上限 100 条 warning | 边界 | 无方案模式 | 添加到 100 再加 | 「检查项最多添加100条」，数量不再增加 | 无 |
| C-18-8 | 提交 filter(d=>d.itemName) 过滤空行 | 功能 | 含空行 | 提交 | 空行不入库 | 无 |
| C-18-9 | 编辑保存走 updateInspectionDetails | 功能 | 编辑模式 | 修改明细保存 | 调用更新明细接口成功 | 无 |
| C-18-10 | schemeSnapshot 快照恢复 | 功能 | 编辑已提交检查 | 打开表单 | 明细从快照解析还原 | 无 |
| C-18-11 | 检查项全空提交拦截 | 负向 | 无明细 | 提交 | 提示至少一条检查项 | 无 |
| C-18-12 | 保存成功返回列表 | 功能 | 合法数据 | 提交 | 成功后 router.back/列表刷新 | 无 |
| C-18-13 | 重复提交防抖 | 负向 | 表单已填 | 双击提交 | 一次请求 | 无 |
| C-18-14 | 方案接口失败提示 | 负向 | mock scheme 500 | 选类型 | 错误提示，方案下拉空 | 无 |

### C19 检查详情（/site/inspection/detail/:id）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-19-1 | result PASS→合格/success，否则不合格/danger | 一致性 | 两种结果记录 | 查看详情 | 标签与颜色正确 | 无 |
| C-19-2 | 明细 result 三态：PASS/FAIL/其他 | 一致性 | 含三态明细 | 核对明细行 | 合格/不合格/未检查 | 无 |
| C-19-3 | schemeName 从快照解析 | 功能 | 方案驱动检查 | 查看详情 | 显示快照中方案名 | 无 |
| C-19-4 | detailItems 空显示 el-empty | 边界 | 无明细记录 | 打开详情 | 空状态组件 | 无 |
| C-19-5 | 详情加载失败提示 | 负向 | mock 失败/非法 id | 打开 | ElMessage.error | 无 |
| C-19-6 | 整改状态与问题描述展示 | 功能 | 有问题记录 | 查看 | 与列表 RECT_MAP 一致 | 无 |
| C-19-7 | 路由参数 id 传递正确 | 边界 | 长 id 记录 | 列表→详情 | 数据加载正确 | 无 |
| C-19-8 | 详情数据与表单提交数据一致 | 一致性 | 刚提交检查 | 打开详情 | 明细逐条一致 | 无 |

### C-SITE-X 现场跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| C-SITE-X1 | 进度计划完成率→项目看板进度仪表盘 | 集成 | 更新多任务 progress 后查项目看板 | 仪表盘数值=计划聚合完成率 | 无 |
| C-SITE-X2 | 检查不合格→整改闭环→档案检查记录 | 集成 | 创建有问题检查→指派→提交→审批通过 | 全链状态流转正确，档案可查 | 无 |
| C-SITE-X3 | 施工日志/进度数据进入项目档案聚合 | 集成 | 录入后切换项目档案 | 档案对应 tab 可见 | 无 |
| C-SITE-X4 | 甘特图任务更新与进度反馈双写一致性 | 集成 | 甘特拖动任务后查进度反馈表 | task-updated 与反馈数据一致 | 无 |

## C-3 行政人事（/hr，5 页，51+3 例）

**业务概述**：人事统计为一次 getHrStatisticsOverview 驱动的 3 卡片+4 ECharts 页；入职/离职申请走标准审批状态机（入职审批通过自动创建系统账号）；办公用品领用与车辆台账为常规 CRUD（vehicle 仅 plateNumber 必填）。

### C20 人事统计（/hr/statistics）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-20-1 | getHrStatisticsOverview 单次加载全部数据 | 功能 | 打开页面 | 抓包 | 仅一次 overview 请求 | L5-API([200,404]) |
| C-20-2 | 3 卡片 totalActive/monthlyEntry/monthlyResign 绑定 | 一致性 | 有在职/入离职数据 | 对比接口 | 数值一致 | 无 |
| C-20-3 | 部门人数柱图数据绑定 | 一致性 | 多部门数据 | 对比图表系列数据 | 与接口 byDept 一致 | 无 |
| C-20-4 | 岗位分布饼图绑定 | 一致性 | 多岗位数据 | 对比 | 一致 | 无 |
| C-20-5 | 工龄分布柱图绑定 | 一致性 | 有工龄数据 | 对比 | 一致 | 无 |
| C-20-6 | 入离职趋势折线图绑定 | 一致性 | 多月数据 | 对比 | 一致 | 无 |
| C-20-7 | 接口失败错误提示 | 负向 | mock 500 | 打开 | ElMessage.error，卡片不显示假数据 | 无 |
| C-20-8 | 空数据各图表空态渲染 | 边界 | 无人员数据 | 打开 | 图表空态/0 值，不报错 | 无 |
| C-20-9 | 窗口 resize 重绘、卸载 dispose | 功能 | 页面已加载 | 缩放窗口/离开页面 | 图表自适应；无内存泄漏告警 | 无 |
| C-20-10 | 卡片与入离职模块源数据一致 | 集成 | 当月有入职/离职审批通过 | 对比 monthlyEntry/Resign 与申请数 | 一致 | 无 |

### C21 入职申请（/hr/entry）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-21-1 | 必填校验 realName/username/phone/orgId/postId | 负向 | 弹窗打开 | 留空提交 | 必填提示 | 无 |
| C-21-2 | 新增成功（DRAFT） | 功能 | 组织/岗位存在 | 完整填写 | 创建草稿 | L5-API |
| C-21-3 | 状态渲染 APPROVED→已通过 否则草稿 | 一致性 | 两态数据 | 核对状态列 | 文案一致 | L5-一致性 |
| C-21-4 | 提交 confirm「通过后自动创建系统账号」 | 功能 | 草稿存在 | 提交→确认 | 文案正确，状态流转 | L5-API |
| C-21-5 | 删除仅 DRAFT | 负向 | APPROVED 记录 | 检查操作列 | 无删除按钮 | 无 |
| C-21-6 | 编辑回填 getHrEntryDetail | 功能 | 草稿存在 | 点编辑 | 详情接口回填表单 | L5-API |
| C-21-7 | 审批通过→自动创建系统账号 | 集成 | 提交并审批通过 | 查系统用户列表 | 新账号存在可登录 | 无 |
| C-21-8 | APPROVED 状态非法再提交 | 负向 | 已通过记录 | 检查操作列 | 无提交按钮 | 无 |
| C-21-9 | 手机号格式边界 | 边界 | 弹窗打开 | 输入 8 位/12 位 | 校验拦截 | 无 |
| C-21-10 | 分页与姓名筛选 | 功能 | 数据>1 页 | 筛选+翻页 | 参数正确 | L5-API/一致性 |
| C-21-11 | 重复提交防抖 | 负向 | 表单已填 | 双击 | 一次请求 | 无 |
| C-21-12 | username 重复边界 | 边界 | 已存在同 username | 再建 | 拒绝或提示 | 无 |

### C22 办公用品领用（/hr/office-supply）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-22-1 | 必填校验 itemName/quantity(min=1) | 负向 | 弹窗打开 | 留空提交 | 必填提示 | 无 |
| C-22-2 | quantity=0 负值拦截 | 边界 | 弹窗打开 | 输入 0 | min=1 拦截 | 无 |
| C-22-3 | 新增/编辑/删除 CRUD | 功能 | 已登录 | 依次操作 | 成功 | L5-API |
| C-22-4 | 状态三元翻译 APPROVED 已领用/PENDING 审批中/其他草稿 | 一致性 | 三态数据 | 核对状态列 | 文案一致 | L5-一致性 |
| C-22-5 | 提交审批（DRAFT） | 功能 | 草稿存在 | 提交 | 状态流转 | 无 |
| C-22-6 | applyNo 单号展示 | 一致性 | 列表有数据 | 核对申请单号列 | 与接口一致 | L5-一致性 |
| C-22-7 | 分页与筛选 | 功能 | 数据>1 页 | 翻页 | 生效 | L5-API |
| C-22-8 | 重复提交防抖 | 负向 | 表单已填 | 双击 | 一次请求 | 无 |
| C-22-9 | 审批通过→用品档案库存扣减 | 集成 | 领用审批通过 | 查 /archive/office-supply | totalIssued 增加/currentStock 减少 | 无 |
| C-22-10 | 领用超库存边界 | 边界 | 库存=5 | 申请 10 | 拦截或提示 | 无 |

### C23 车辆管理（/hr/vehicle）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-23-1 | 仅 plateNumber 必填 | 负向 | 弹窗打开 | 留空车牌提交 | 必填提示；其余可为空 | 无 |
| C-23-2 | 新增/编辑/删除 CRUD | 功能 | 已登录 | 依次操作 | 成功 | L5-API |
| C-23-3 | vehicleStatus IN_USE 使用中/其他闲置 | 一致性 | 两态数据 | 核对状态列 | 文案一致 | L5-一致性 |
| C-23-4 | insuranceExpiry/inspectionExpiry 日期录入 | 功能 | 弹窗打开 | 选择日期保存 | 正确回显 | L5-一致性 |
| C-23-5 | 8 列字段一致性 | 一致性 | 列表有数据 | 核对 | 严格一致 | L5-一致性 |
| C-23-6 | 分页与筛选 | 功能 | 数据>1 页 | 翻页 | 生效 | L5-API |
| C-23-7 | 重复车牌边界 | 边界 | 已存在同车牌 | 再建 | 拒绝或提示 | 无 |
| C-23-8 | 到期日临近提醒（保险/年检） | 边界 | 到期日<30 天 | 查看列表 | 显示符合实现（若无则为盲点） | 无 |
| C-23-9 | 重复提交防抖 | 负向 | 表单已填 | 双击 | 一次请求 | 无 |
| C-23-10 | 接口失败提示 | 负向 | mock 500 | 保存 | ElMessage.error | 无 |

### C24 离职申请（/hr/resign-apply）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-24-1 | userId 必填（el-input-number min=1） | 负向 | 弹窗打开 | 留空/输 0 提交 | 校验拦截 | 无 |
| C-24-2 | isHandover switch 录入 | 功能 | 弹窗打开 | 切换开关保存 | 1/0 正确保存 | 无 |
| C-24-3 | 仅新增+提交：无编辑/删除入口 | 负向 | 列表有数据 | 检查操作列 | 无编辑/删除按钮 | 无 |
| C-24-4 | 提交仅 DRAFT | 功能 | 草稿存在 | 提交 | 状态流转 | 无 |
| C-24-5 | 状态列 RESIGN_STATUS 枚举渲染 | 一致性 | 多态数据 | 核对 | 与枚举基线一致 | L5-一致性 |
| C-24-6 | isHandover 1/0→是/否、交接人列 | 一致性 | 两种数据 | 核对 | 文案一致 | L5-一致性 |
| C-24-7 | 分页（view 与 consistency 接口路径差异需统一） | 一致性 | 数据>1 页 | 抓包翻页 | 以实际 API 为准 | L5-API(仅[200,404]) |
| C-24-8 | 重复提交防抖 | 负向 | 表单已填 | 双击 | 一次请求 | 无 |
| C-24-9 | 审批通过→人事统计 monthlyResign+1、在职-1 | 集成 | 离职审批通过 | 查 /hr/statistics | 数值联动变化 | 无 |

### C-HR-X 人事跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| C-HR-X1 | 入职审批通过→系统账号创建→可登录 | 集成 | 入职全流程 | 账号存在且能登录系统 | 无 |
| C-HR-X2 | 办公用品领用审批通过→档案台账库存联动 | 集成 | 领用→审批→查档案 | currentStock/totalIssued 正确 | 无 |
| C-HR-X3 | 入职/离职→人事统计卡片与趋势图联动 | 集成 | 当月入离职各一 | monthlyEntry/monthlyResign 与趋势末点正确 | 无 |

## C-4 档案管理（/archive，4 页，34+3 例）

**业务概述**：四页均为只读档案视图（页首 el-alert 只读提示）。首页为「项目下拉（/v1/project/list）→ handleProjectChange → getProjectArchive 聚合视图（6 tabs+基本信息）」，加载失败 ElMessage.error('加载项目档案失败：')+resetArchive；其余三页为 keyword 筛选的只读列表（历史 Promise.resolve 假实现已于 commit 17ee02c 修复）。

### C25 项目档案（/archive/index）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-25-1 | 只读 alert 展示 | 功能 | 打开页面 | 查看页首 | 只读提示可见 | 无 |
| C-25-2 | 项目下拉加载 /v1/project/list | 功能 | 项目存在 | 打开页面 | 下拉项与 list 一致 | L5-一致性 |
| C-25-3 | handleProjectChange→getProjectArchive 聚合加载 | 功能 | 选中有数据项目 | 选择项目 | 基本信息+6 tabs 渲染 | L5-API |
| C-25-4 | 6 tabs（成员/施工合同/付款/收款/分包/机械）数量徽标 | 一致性 | 聚合数据非空 | 对比 tab 数量与数组长度 | 一致 | 无 |
| C-25-5 | 基本信息 contractAmount/totalIncome/totalExpense formatMoney | 一致性 | 有金额数据 | 对比接口 | 千分位 2 位小数 | 无 |
| C-25-6 | formatMoney NaN/空值兜底 '-' | 边界 | 字段为 null/NaN | 查看金额 | 显示 '-' 不显示 NaN | 无 |
| C-25-7 | 加载失败 ElMessage.error+resetArchive | 负向 | mock 档案接口 500 | 选项目 | 错误提示，视图重置 | 无 |
| C-25-8 | 切换项目重新加载（旧数据不残留） | 边界 | 已加载项目 A | 切换到 B | A 数据清空，B 数据加载 | 无 |
| C-25-9 | 未选择项目空状态 | 边界 | 首次打开 | 不选项目 | 空态提示，不发档案请求 | 无 |
| C-25-10 | 档案数据与各源模块一致（付款/收款 tab vs 财务登记） | 集成 | 项目有收付款 | 对比 tab 明细与财务页 | 一致 | 无 |

### C26 办公用品档案（/archive/office-supply）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-26-1 | 只读 alert 展示 | 功能 | 打开页面 | 查看 | 只读提示可见 | 无 |
| C-26-2 | keyword 筛选 | 功能 | 多用品数据 | 输入关键字查询 | 请求携带 keyword，结果过滤 | 无 |
| C-26-3 | 5 列绑定 supplyName/currentStock/totalInbound/totalIssued/lastInboundDate | 一致性 | 列表有数据 | 核对 | 严格一致 | L5-一致性 |
| C-26-4 | 分页 total 一致 | 一致性 | 数据>1 页 | 翻页 | total 一致 | L5-一致性 |
| C-26-5 | 空数据空态 | 边界 | 无匹配 | 查询 | 空态组件 | 无 |
| C-26-6 | 数值列 0 值显示 0（非 '-'） | 边界 | 库存为 0 | 查看 | 显示 0 | 无 |
| C-26-7 | 接口失败提示 | 负向 | mock 500 | 加载 | ElMessage.error | 无 |
| C-26-8 | 库存=累计入库-累计领用 勾稽 | 一致性 | 有出入库记录 | 对比三列 | 等式成立 | L5-API |

### C27 其它支出合同档案（/archive/other-expense-contract）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-27-1 | 只读 alert 展示 | 功能 | 打开页面 | 查看 | 只读提示可见 | 无 |
| C-27-2 | keyword 筛选 | 功能 | 多合同数据 | 输入关键字 | 正确过滤 | 无 |
| C-27-3 | contractAmount formatMoney、空值 '-' | 一致性 | 含空金额记录 | 核对金额列 | 格式正确、空为 '-' | L5-一致性 |
| C-27-4 | status 原始 code 直出（无翻译） | 一致性 | 列表有数据 | 核对状态列 | 显示原始 code | L5-一致性 |
| C-27-5 | 分页 total 一致 | 一致性 | 数据>1 页 | 翻页 | total 一致 | L5-一致性 |
| C-27-6 | 空数据空态 | 边界 | 无匹配 | 查询 | 空态组件 | 无 |
| C-27-7 | 接口失败提示 | 负向 | mock 500 | 加载 | ElMessage.error | 无 |
| C-27-8 | 档案合同与支出域其他合同模块数据一致 | 集成 | 存在其他支出合同 | 对比源模块列表 | 编号/金额/状态一致 | L5-API |

### C28 其它收入合同档案（/archive/other-income-contract）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-28-1 | 只读 alert 展示 | 功能 | 打开页面 | 查看 | 只读提示可见 | 无 |
| C-28-2 | keyword 筛选 | 功能 | 多合同数据 | 输入关键字 | 正确过滤 | 无 |
| C-28-3 | contractAmount formatMoney、空值 '-' | 一致性 | 含空金额记录 | 核对 | 格式正确 | L5-一致性 |
| C-28-4 | status 原始 code 直出 | 一致性 | 列表有数据 | 核对 | 原始 code | L5-一致性 |
| C-28-5 | 分页 total 一致 | 一致性 | 数据>1 页 | 翻页 | total 一致 | L5-一致性 |
| C-28-6 | 空数据空态 | 边界 | 无匹配 | 查询 | 空态组件 | 无 |
| C-28-7 | 接口失败提示 | 负向 | mock 500 | 加载 | ElMessage.error | 无 |
| C-28-8 | 收入合同档案与收款/开票口径关联一致 | 集成 | 存在收入合同+回款 | 对比金额链路 | 合同额→回款累计勾稽 | 无 |

### C-ARC-X 档案跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| C-ARC-X1 | 财务登记→档案聚合：回款/付款新增后项目档案 tab 实时可见 | 集成 | 登记收付款→查档案 | tab 数据即时包含新记录 | 无 |
| C-ARC-X2 | 办公用品出入库→档案台账三列勾稽 | 集成 | 入库 50/出库 10→查档案 | totalInbound/totalIssued/currentStock 正确 | 无 |
| C-ARC-X3 | 项目删除/归档后档案入口行为 | 边界 | 删除项目后打开档案 | 下拉不含该项目，不报错 | 无 |

## C-5 首页看板（/dashboard，2 页，26+3 例）

**业务概述**：首页为公司级 KPI（4 statCards，formatWan = /10000 toFixed(1)）+ ECharts 饼图（项目状态分布）+柱图（收支对比），三个加载函数均有独立 try/catch 且**当前源码已显式 ElMessage.error**（catch 中饼/柱图仍渲染空图）。项目看板为 ProjectSelector 驱动的四维度并行加载（loadDimension 独立 try/catch，失败维度 el-alert 显式报错、空维度 el-empty），四个渲染函数含数值变换（剩余预算 Math.max(…,0)、完成率 ×100 裁剪 0–100、金额 toWan），resize 防抖 300ms。

### C29 首页驾驶舱（/dashboard）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-29-1 | greeting 按时段切换 | 边界 | 系统时间 0/12/18 点附近 | 打开页面 | 问候语与时段匹配 | 无 |
| C-29-2 | 4 statCards 渲染（项目总数/合同总额万/已收款万/垫资万） | 功能 | overview 有数据 | 打开页面 | 四卡片渲染 | L5-一致性 |
| C-29-3 | formatWan：(val/10000).toFixed(1) | 一致性 | 合同总额=1,234,567 | 核对卡片 | 显示 123.5 | L5-一致性 |
| C-29-4 | 空值/NaN→'0' | 边界 | 字段为 null | 核对卡片 | 显示 0 而非 NaN | L5-一致性 |
| C-29-5 | loadStats 失败→ElMessage.error 显式提示（静默兜底缺陷回归） | 负向 | mock company-overview 500 | 打开页面 | 出现错误提示，卡片空态 | 无 |
| C-29-6 | 饼图数据绑定（项目状态分布） | 一致性 | 多状态项目 | 对比图例数据与接口 | 一致 | L5-API |
| C-29-7 | 饼图失败→ElMessage.error+渲染空饼图 | 负向 | mock 饼图接口 500 | 打开 | 错误提示+空图 | 无 |
| C-29-8 | 柱图失败→ElMessage.error+渲染空柱图 | 负向 | mock 柱图接口 500 | 打开 | 错误提示+空图 | 无 |
| C-29-9 | KPI 与收支数据源一致（已收款=回款登记汇总口径） | 集成 | 有回款数据 | 对比财务回款合计 | 口径一致 | 无 |
| C-29-10 | 窗口 resize 图表重绘 | 功能 | 页面已加载 | 缩放窗口 | 图表自适应 | 无 |
| C-29-11 | projectCount=0 空态 | 边界 | 无项目 | 打开 | 卡片 0、图表空态不报错 | 无 |
| C-29-12 | consistency spec __silentFallback__ 硬编码记录与已修复源码脱节 | 一致性 | 运行 consistency/dashboard | 查看报告 | 该项仍被固定写入发现（测试资产过期，需更新） | L5-一致性(过期断言) |

### C30 项目看板（/project-dashboard）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| C-30-1 | 选项目→resetDimensions+loadDashboard 四维并行 | 功能 | 有项目数据 | 选择项目 | 四维度接口并行发起 | L5-API |
| C-30-2 | 单维度失败→仅该维度 el-alert | 负向 | mock budget 接口 500 | 选项目 | 预算区 alert 报错，其余三维正常 | 无 |
| C-30-3 | 维度数据为空→el-empty | 边界 | 项目无进度数据 | 选项目 | 进度区空态 | 无 |
| C-30-4 | 预算饼图：剩余预算 Math.max(budget-used,0) | 边界 | 支出>预算 | 选项目 | 剩余预算显示 0 不负数 | 无 |
| C-30-5 | 进度仪表盘：completionRate*100 裁剪 0–100 | 边界 | 完成率 1.2/负值 | 选项目 | 仪表盘 100/0 | L5-API |
| C-30-6 | 合同柱图 toWan 换算 | 一致性 | 有合同数据 | 对比柱值与接口/10000 | 一致 | L5-API |
| C-30-7 | 产值折线图 toWan 换算 | 一致性 | 多月产值 | 对比 | 一致 | L5-API |
| C-30-8 | watch 驱动：切换项目旧图 dispose 新图 render | 功能 | 两项目有数据 | 连续切换 | 无残影/重复渲染 | 无 |
| C-30-9 | resize 防抖 300ms | 边界 | 页面已加载 | 快速连续缩放 | 防抖后仅一次重绘 | 无 |
| C-30-10 | 未选项目空状态，不发维度请求 | 边界 | 首次打开 | 不选项目 | 空态提示，无请求 | L5-一致性(__note__) |
| C-30-11 | 不存在项目（999999999）→404 错误展示 | 负向 | URL 直改非法 id | 打开 | alert 报错而非崩溃 | L5-API(404) |
| C-30-12 | 快速切换项目竞态 | 边界 | 两项目 | 极速来回切换 | 最终视图=最后所选项目，无串数据 | 无 |
| C-30-13 | 维度数据与源模块一致（预算 vs 预算模块、产值 vs 产值上报） | 集成 | 项目有预算与产值 | 对比源模块 | 一致 | 无 |
| C-30-14 | 四维度 overview 聚合与分接口一致 | 一致性 | 有完整数据 | 对比 overview 与四单接口 | 数值一致 | L5-API |

### C-DSB-X 看板跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| C-DSB-X1 | 回款登记新增→首页「已收款(万)」与项目看板合同回款柱同步增长 | 集成 | 登记回款→刷新两看板 | 两处同步 +Δ | 无 |
| C-DSB-X2 | 付款审批通过→项目看板预算饼图已用额增长、剩余预算减少 | 集成 | 付款审批通过→查项目看板 | 数值联动 | 无 |
| C-DSB-X3 | 进度计划 progress 更新→项目看板进度仪表盘联动 | 集成 | 更新计划进度→查看板 | completionRate 变化一致 | 无 |

---

# 分组 D：平台支撑域（353 例）

> 页序约定：D1-D3 登录与个人中心 · D4-D17 系统管理 · D18-D24 基础数据 · D25-D28 消息 · D29-D33 工作流 · D34-D36 平台管理

## D-1 登录与个人中心（/login · /forgot-password · /user，3 页）

**业务概述**：login/index.vue 三字段均 required，登录前 getImageCaptcha() 获取 uuid+imageBase64，提交 /v1/auth/login 带 captchaUuid，失败 catch 中自动 refreshCaptcha()，成功后 setToken/setUserInfo/setPermissions 再 push('/')。forgot-password.vue 为三步 el-steps 流程（发码→验码→重置），含手机号/6位验证码/密码强度三套正则与 60s 倒计时。devices.vue 对当前设备禁止注销（tooltip「当前设备不可注销」）、status!==1 禁用注销按钮。注意：背景提及的「记住用户名」在登录页源码中**无对应控件**。

### D1 登录（/login）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-1-1 | 完整登录成功跳转 | 功能 | 有效账号、正确验证码 | 填写用户名/密码/验证码，点击登录 | 请求带 captchaUuid，成功写 token/userInfo/permissions 并 push('/') | L5-UI/L5-API |
| D-1-2 | 空表单必填拦截 | 负向 | 无 | 三字段留空直接提交 | username/password/captcha 均报 required 错误，不发请求 | L5-UI |
| D-1-3 | 验证码图片加载 | 功能 | 无 | 打开登录页 | img 渲染 base64，uuid 已持有 | L5-UI |
| D-1-4 | 错误验证码→自动刷新 | 负向 | 有效账密 | 输入错误验证码提交 | 登录失败提示，验证码图自动更换（refreshCaptcha） | 无专项 |
| D-1-5 | 验证码 uuid 一次性消费 | 负向 | 已消费过一次 uuid | 用同一 uuid 再次提交 | 后端拒绝，前端提示并刷新验证码 | 无 |
| D-1-6 | IP 失败锁定（5次/5分钟→锁15分钟） | 负向 | 连续 5 次错误密码 | 第 6 次用正确密码登录 | 提示锁定剩余时间，拒绝登录 | 无 |
| D-1-7 | 锁定态下验证码仍刷新 | 边界 | 已锁定 | 点击验证码图 | 图片刷新但提交仍被拒 | 无 |
| D-1-8 | 登录后权限点装载 | 集成 | 登录成功 | 观察 store.permissions | setPermissions 已写入，v-permission 可用 | L1 |
| D-1-9 | 已认证访问 /login | 权限 | 已登录 | 直接访问 /login | 路由守卫重定向/不重复渲染 | L5-UI/L1 |
| D-1-10 | 记住用户名 | 功能 | 背景需求 | 勾选记住后二次进入 | 用户名回填 | 无（源码无此控件，盲点） |

### D2 忘记密码（/forgot-password）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-2-1 | 三步流程正向走通 | 功能 | 已注册手机号 | 发码→输 6 位码→设新密码 | 依次 sendResetCode/verifyResetCode/resetPassword，提示成功 | 无 |
| D-2-2 | 手机号格式校验 | 负向 | 无 | 输入 12345678901 / 2 开头号码 | pattern /^1[3-9]\d{9}$/ 拦截 | 无 |
| D-2-3 | 验证码格式校验 | 负向 | 已进入第 2 步 | 输入 5 位/含字母 | /^\d{6}$/ 拦截 | 无 |
| D-2-4 | 错误验证码 | 负向 | 已发码 | 输入错误 6 位码 | verifyResetCode 失败提示，不进入第 3 步 | 无 |
| D-2-5 | 新密码强度校验 | 负向 | 已进入第 3 步 | 输入纯数字 7 位 / 无数字 | (?=.*[A-Za-z])(?=.*\d).{8,20} 拦截 | 无 |
| D-2-6 | 确认密码不一致 | 负向 | 已进入第 3 步 | 两次密码不同 | 一致性 validator 报错 | 无 |
| D-2-7 | 60s 倒计时与重发 | 边界 | 已点击发送 | 观察按钮并等待 60s | 倒计时期间禁用，归零后可重发 | 无 |
| D-2-8 | 脱敏手机号展示 | 功能 | 输入 13812345678 | 进入第 2 步 | 显示 138****5678 | 无 |
| D-2-9 | 重置成功后回登录页 | 集成 | 重置完成 | 点击返回登录 | 跳转 /login，新密码可登录 | 无 |

### D3 登录设备（/user/devices）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-3-1 | 设备列表渲染 | 功能 | 多设备在线 | 打开页面 | 列表含设备/位置/时间/状态列 | L5-一致性 |
| D-3-2 | 当前设备禁注销 | 功能 | isCurrent=true 行存在 | 查看该行按钮 | disabled + tooltip「当前设备不可注销」 | L5-一致性 |
| D-3-3 | 注销其他设备 | 功能 | 存在非当前在线设备 | 点注销→confirm 确认 | ElMessageBox 确认后调 revoke，列表刷新 | L5-一致性 |
| D-3-4 | 注销取消 | 负向 | 同上 | confirm 点取消 | 不发请求，列表不变 | 无 |
| D-3-5 | 离线设备禁注销 | 边界 | row.status!==1 | 查看注销按钮 | disabled | 无 |
| D-3-6 | 位置格式化 | 功能 | location 含 \| 分隔 | 渲染列表 | split('\|').join(' ') 显示空格分隔 | 无 |
| D-3-7 | 空列表占位 | 边界 | 仅当前设备/无数据 | 打开页面 | el-empty 展示 | 无 |
| D-3-8 | 未登录访问设备页 | 权限 | 未登录 | 直接访问 /user/devices | 路由守卫重定向 /login | L1 |

### D-X1 登录域跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| D-3-9 | 多设备上限踢出联动 | 集成 | 第 6 台设备登录（max-devices=5） | 最早设备被踢，其设备页/会话失效提示 | 无 |
| D-3-10 | 异地登录消息→设备页 | 集成 | 消息中心点「查看设备」 | router.push('/user/devices') 且设备列表含新设备 | 无 |
| D-3-11 | 租户停用→登录拦截 | 集成 | 平台页停用租户后用其账号登录 | 登录被拒并提示 | 无 |

## D-2 系统管理（/system，14 页）

**业务概述**：14 页覆盖组织树（左树右详情）、用户 CRUD（username 编辑时 :disabled="!!formData.id"、批量操作经 filterBatchIds 排除自己）、角色（菜单树 show-checkbox+check-strictly、全选 indeterminate 态、dataScope 五档）、菜单（DIR/MENU/BUTTON 条件字段）、字典两级、系统设置（valueType 分型渲染、仅保存 diff 项）、模板（handleFileChange **只弹 info 未真正上传**）、打印模板（{{变量}} 正则预览）、日志三 tab、编号规则（L1 属性测试在案）、备份（恢复走 449 密码二次确认拦截器）、版本（semver pattern）、监控（**仅 el-empty 占位**）。

### D4 组织机构（/system/org）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-4-1 | 组织树加载+选中详情 | 功能 | 存在多级组织 | 点击树节点 | 右侧详情联动 | L5-API |
| D-4-2 | 新增组织必填校验 | 负向 | 无 | orgName/orgType 留空提交 | required 拦截 | L5-API(相关) |
| D-4-3 | 新增子组织 | 功能 | 选中父节点 | 新增并保存 | 树刷新出现子节点 | L5-API |
| D-4-4 | 停用组织 | 功能 | 启用中节点 | 停用→confirm | 节点打 danger tag | 无 |
| D-4-5 | 启用组织 | 功能 | 停用节点 | 启用→confirm | tag 恢复 | 无 |
| D-4-6 | 删除组织二次确认 | 负向 | 叶子节点 | 删除→取消 | 不删除 | L5-API |
| D-4-7 | 名称重复冲突 | 负向 | 已存在同名 | 新增同名 | 后端 409/错误提示 | 无 |
| D-4-8 | 无权限访问组织页 | 权限 | 无 system:org 权限 | 访问 /system/org | 守卫拦截/按钮经 v-permission 隐藏 | L1 |
| D-4-9 | 停用含子节点组织 | 边界 | 节点有子级 | 停用 | 提示或级联按后端规则，前端不崩 | 无 |

### D5 用户管理（/system/user）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-5-1 | 新增用户全必填 | 负向 | 无 | username/realName/phone/orgId/password 逐项留空 | 均 required 拦截 | L5-API |
| D-5-2 | 新增用户成功 | 功能 | 组织树已加载 | 填全表单提交 | 创建成功列表刷新 | L5-API |
| D-5-3 | 编辑时用户名锁定 | 功能 | 已有用户 | 点编辑 | username 输入框 disabled（!!formData.id） | L5-一致性 |
| D-5-4 | 分配角色回显 | 功能 | 用户已有角色 | 打开分配弹窗 | checkbox-group 初始为 row.roleIds | L5-一致性 |
| D-5-5 | 重置密码二次确认 | 功能 | 目标用户存在 | 重置密码→confirm | 确认后调 reset-password | L5-API |
| D-5-6 | 批量启停用排除自己 | 功能 | 勾选含当前登录用户 | 批量停用 | filterBatchIds 过滤自己；仅选自己提示「不能对自己执行此操作」 | L1(batch-status.property) |
| D-5-7 | 用户名重复 409 | 负向 | 已存在 username | 新增同名 | 冲突提示 | 无 |
| D-5-8 | 手机号格式 | 负向 | 无 | 输入非法手机号 | 校验拦截 | 无 |
| D-5-9 | 分页+条件查询 | 功能 | 数据>10条 | 输入条件查询、翻页 | pageNum/pageSize 正确传递 | L5-API |
| D-5-10 | 删除用户确认 | 负向 | 目标用户存在 | 删除→取消 | 不删除 | L5-API |
| D-5-11 | 无权限隐藏操作按钮 | 权限 | 无 user:edit 权限 | 进入页面 | 编辑按钮经 v-permission 隐藏 | L1 |

### D6 角色管理（/system/role）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-6-1 | 新增角色必填 | 负向 | 无 | roleName/roleCode 留空 | required 拦截 | L5-API |
| D-6-2 | 角色 CRUD | 功能 | 无 | 增/查/改 | 成功 | L5-API |
| D-6-3 | 菜单权限树勾选 | 功能 | 打开权限配置 | 勾选节点保存 | GET/PUT role/{id}/menus 一致 | L5-API |
| D-6-4 | check-strictly 父子不联动 | 功能 | 树多层 | 仅勾父节点 | 子节点不自动选中（check-strictly） | 无 |
| D-6-5 | 全选/全不选+半选态 | 功能 | 权限树已加载 | 全选→部分取消 | indeterminate 正确切换（updateCheckAllState） | 无 |
| D-6-6 | 数据权限五档 | 功能 | 角色存在 | 依次选 ALL/DEPT_AND_CHILDREN/DEPT/PROJECT/SELF 保存 | PUT data-scope 成功 | L5-API |
| D-6-7 | 角色编码重复 | 负向 | 已存在 roleCode | 新增同码 | 冲突提示 | 无 |
| D-6-8 | 删除已绑定用户角色 | 边界 | 角色已分配用户 | 删除 | 后端约束提示，前端不崩 | 无 |
| D-6-9 | 无权限访问 | 权限 | 无 role 权限 | 访问页面 | 守卫拦截 | L1 |
| D-6-10 | 权限变更后实时生效 | 集成 | 修改角色菜单 | 该角色用户重新进入 | 菜单/按钮按新权限显隐 | 无 |

### D7 菜单管理（/system/menu）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-7-1 | 树表全展开渲染 | 功能 | 菜单树存在 | 进入页面 | default-expand-all 全展开 | L5-API |
| D-7-2 | 全部折叠/展开切换 | 功能 | 树多层 | 点切换 | refreshTable+nextTick 重建生效 | 无 |
| D-7-3 | menuName/menuType 必填 | 负向 | 无 | 留空提交 | required 拦截 | 无 |
| D-7-4 | BUTTON 类型条件字段 | 功能 | 选 menuType=BUTTON | 观察表单 | 显示权限标识、隐藏组件路径 | 无 |
| D-7-5 | MENU 类型条件字段 | 功能 | 选 MENU | 观察表单 | 显示组件路径 | 无 |
| D-7-6 | 新增子菜单 | 功能 | 选中父级 | 新增保存 | 树中出现子节点 | 无 |
| D-7-7 | 权限标识格式 | 边界 | BUTTON | 输入非 x:x:x 格式 | 按校验规则提示（如有）/后端约束 | 无 |
| D-7-8 | 用户菜单接口一致 | 一致性 | 已配置权限 | 对比 menu 树与 menu/user | 当前用户菜单为全树子集 | L5-API |
| D-7-9 | 删除含子级菜单 | 边界 | 菜单有子节点 | 删除 | 后端拒绝或级联提示 | 无 |

### D8 字典管理（/system/dict）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-8-1 | 字典 CRUD | 功能 | 无 | 增改查删 | 成功 | L5-API |
| D-8-2 | dictName/dictCode 必填 | 负向 | 无 | 留空提交 | required 拦截 | L5-API(相关) |
| D-8-3 | 前端名称/编码过滤 | 功能 | 字典>1页 | 输入关键字 | 左侧列表前端 filter 即时过滤 | 无 |
| D-8-4 | 字典值新增必填 | 负向 | 选中字典 | label/value 留空 | required 拦截 | 无 |
| D-8-5 | 字典值新增子项 | 功能 | 选中值节点 | 新增子项 | 树形值结构生效 | 无 |
| D-8-6 | 字典编码重复 | 负向 | 已存在 dictCode | 新增同码 | 冲突提示 | 无 |
| D-8-7 | useDict 消费端渲染 | 集成 | 字典已建 | 业务页用 useDict 取该字典 | 下拉项与字典值一致 | L1(use-dict) |
| D-8-8 | 删除被引用字典 | 边界 | 字典被表单使用 | 删除 | 后端约束提示 | 无 |

### D9 岗位管理（/system/post）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-9-1 | 岗位 CRUD | 功能 | 无 | 增改查删 | 成功 | L5-API/L5-一致性 |
| D-9-2 | postName/postCode 必填 | 负向 | 无 | 留空提交 | required 拦截 | L5-一致性 |
| D-9-3 | 排序边界 | 边界 | 无 | sort 输入 -1 / 10000 | min=0/max=9999 钳制 | 无 |
| D-9-4 | 启用/停用确认 | 功能 | 岗位存在 | 切换状态→confirm | 状态更新 | 无 |
| D-9-5 | 删除确认+取消 | 负向 | 岗位存在 | 删除→取消 | 不删除 | L5-API |
| D-9-6 | 岗位编码重复 | 负向 | 已存在 postCode | 新增同码 | 冲突提示 | 无 |
| D-9-7 | 删除已绑定用户岗位 | 边界 | 岗位已绑用户 | 删除 | 后端约束提示 | 无 |
| D-9-8 | 条件查询+分页 | 功能 | 数据充足 | 查询翻页 | 正确 | L5-API |

### D10 系统设置（/system/config）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-10-1 | 四分组 tab 切换加载 | 功能 | 无 | 切 security/approval/file/notification | 各组按 group 拉取渲染 | L5-API |
| D-10-2 | NUMBER 型 min/max 钳制 | 边界 | NUMBER 项有 valueRange | 输入越界值 | input-number 从 valueRange 正则解析 min/max 并钳制 | 无 |
| D-10-3 | BOOLEAN 型 switch 切换 | 功能 | BOOLEAN 项 | 切换开关 | active-value 写入 | 无 |
| D-10-4 | JSON 型非法值 | 负向 | JSON 项 | 输入非法 JSON 保存 | 校验拦截/报错 | 无 |
| D-10-5 | 仅保存 diff 项 | 功能 | 修改 1 项 | 保存 | batchUpdateConfig 只含 currentVal!==originalVal 项 | 无 |
| D-10-6 | 无修改保存提示 | 边界 | 未修改 | 点保存 | 提示「没有需要保存的修改」，不发请求 | 无 |
| D-10-7 | 恢复默认值 | 功能 | 项已改 | 恢复默认→confirm | resetConfigToDefault 生效 | 无 |
| D-10-8 | STRING 超长输入 | 边界 | STRING 项 | 输入超长文本 | 按后端约束提示 | 无 |
| D-10-9 | 安全配置影响登录策略 | 集成 | 修改锁定次数配置 | 触发失败登录 | 锁定阈值按新配置生效 | 无 |
| D-10-10 | 无权限访问 | 权限 | 无 config 权限 | 访问页面 | 守卫拦截 | L1 |

### D11 通用模板（/system/template）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-11-1 | 模板 CRUD | 功能 | 无 | 增改查删 | 成功 | L5-一致性 |
| D-11-2 | 模块 9 选项渲染 | 功能 | 无 | 打开表单 | moduleOptions 9 项完整 | L5-一致性 |
| D-11-3 | IMPORT/EXPORT/PRINT 类型区分 | 功能 | 无 | 切换类型 | 表单/列表按类型展示 | 无 |
| D-11-4 | 文件选择未真正上传 | 负向 | IMPORT 类型 | 选择文件 | **仅 ElMessage.info('已选择文件...')，fileId 恒 null**（源码缺陷，盲点） | 无 |
| D-11-5 | 设默认模板互斥 | 功能 | 同模块多模板 | 开启某模板 isDefault | 同模块仅一个默认 | 无 |
| D-11-6 | PRINT 模板编辑内容弹窗 | 功能 | PRINT 行 | 点「编辑内容」 | 弹窗含 {{变量名}} 占位符提示 | 无 |
| D-11-7 | 必填校验 | 负向 | 无 | 名称留空 | required 拦截 | 无 |
| D-11-8 | 模板被推送配置引用 | 集成 | push-config 选择模板 | loadTemplates | 下拉含该模板（page:1,size:200） | 无 |
| D-11-9 | 删除使用中模板 | 边界 | 模板被引用 | 删除 | 后端约束提示 | 无 |

### D12 打印模板（/system/print-template）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-12-1 | 模板 CRUD | 功能 | 无 | 增改查删 | 成功 | L5-一致性 |
| D-12-2 | templateName 必填+长度 | 边界 | 无 | 留空/输 101 字符 | required 与 max:100 拦截 | L5-一致性 |
| D-12-3 | businessType 6 选项 | 功能 | 无 | 打开表单 | CONTRACT/BUDGET/MATERIAL/FINANCE/LABOR/MACHINE | 无 |
| D-12-4 | 引擎类型切换 | 功能 | 无 | SIMPLE↔THYMELEAF | engineType 正确提交 | 无 |
| D-12-5 | templateContent 必填 | 负向 | 无 | 内容留空 | required 拦截 | 无 |
| D-12-6 | 名称前端过滤 | 功能 | 数据多页 | 输入名称 | 前端过滤（后端无模糊查询参数，源码注释明示） | 无 |
| D-12-7 | 预览变量提取 | 功能 | 内容含 {{a}} {{b.c}} | 点预览 | 正则提取变量填「示例-x」，renderPrintTemplate 新窗口 | 无 |
| D-12-8 | 预览渲染为空 | 负向 | 无变量可渲染 | 预览 | 提示「渲染结果为空」 | 无 |
| D-12-9 | Thymeleaf 占位符兼容 | 边界 | engineType=THYMELEAF | 预览 | ${} 语法不被 {{}} 正则误处理 | 无 |
| D-12-10 | 默认模板互斥 | 功能 | 同业务多模板 | 设默认 | 互斥生效 | 无 |

### D13 日志管理（/system/log）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-13-1 | 三 tab 列结构 | 功能 | 无 | 切 oper/login/exception | 三套列各自正确 | L5-API/L5-一致性 |
| D-13-2 | operator 条件查询 | 功能 | 有日志 | 输入操作人查询 | 结果过滤 | 无 |
| D-13-3 | 时间范围查询 | 功能 | 有日志 | 选 startTime/endTime | 区间过滤 | 无 |
| D-13-4 | 时间范围倒置 | 边界 | 无 | start>end | 空结果或提示，不崩 | 无 |
| D-13-5 | 分页翻页 | 功能 | 数据充足 | 翻页 | 正确 | L5-API |
| D-13-6 | 登录日志 tab 数据 | 一致性 | 刚执行过登录 | 查看 login tab | 含刚次登录记录 | 无 |
| D-13-7 | 批量删除 | 负向 | 背景需求 | 勾选多条批删 | **源码无批删按钮**（差距，不可执行） | 无 |
| D-13-8 | 无权限访问 | 权限 | 无 log 权限 | 访问 | 守卫拦截 | L1 |

### D14 编号规则（/system/serial-number）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-14-1 | 规则 CRUD | 功能 | 无 | 增改查删 | 成功 | L5-一致性 |
| D-14-2 | businessType 格式校验 | 负向 | 无 | 输入中文/超 50 字符 | validateBusinessType「仅字母数字下划线≤50」拦截 | L1(property) |
| D-14-3 | rulePrefix 长度 | 边界 | 无 | 输入 21 字符 | max:20 拦截 | L1(property) |
| D-14-4 | dateFormat 三选一 | 功能 | 无 | yyyyMMdd/yyyyMM/yyyy 切换 | 预览随动 | L1(property) |
| D-14-5 | seqLength 边界 1 与 10 | 边界 | 无 | 输入 0/11 | validator 拦截；1 与 10 通过 | L1(property) |
| D-14-6 | 补零正确性 | 功能 | seqLength=4 | 预览 | generateSerialNumber 返回 4 位补零序列 | L1(property)/L5-一致性 |
| D-14-7 | resetPeriod 切换 | 功能 | 无 | MONTH/YEAR | 提交正确 | 无 |
| D-14-8 | 同 businessType 重复规则 | 负向 | 已有规则 | 再建同业务类型 | 冲突提示 | 无 |
| D-14-9 | 预览实时联动 | 功能 | 改任意参数 | 观察预览 | 即时重新生成 | L1(property) |

### D15 数据备份（/system/backup）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-15-1 | 手动备份 | 功能 | 无 | 备份→confirm | MANUAL 记录生成 | L5-一致性 |
| D-15-2 | 并发备份 409 守卫 | 负向 | 备份进行中 | 再次触发 | 409 冲突提示 | 无 |
| D-15-3 | 下载按钮启用条件 | 边界 | status!=='SUCCESS' 或无 storagePath | 查看按钮 | disabled | L5-一致性 |
| D-15-4 | 恢复二次确认 | 功能 | SUCCESS 记录 | 恢复→confirm(type=error) | 确认后发请求 | 无 |
| D-15-5 | 恢复 449 密码确认链 | 集成 | 后端返回 449 | 恢复 | 拦截器弹密码框，验证后自动重试 | L1(组件级，端到端无) |
| D-15-6 | 449 密码错误 | 负向 | 449 弹窗 | 输错密码 | 重试失败提示 | L1(组件级) |
| D-15-7 | SCHEDULED/MANUAL 标签 | 功能 | 两类记录 | 查看列表 | tag 正确 | 无 |
| D-15-8 | formatSize/formatDuration 边界 | 边界 | 值为 0 | 渲染 | 显示 '-' | 无 |
| D-15-9 | 备份进行中状态轮显 | 功能 | 备份中 | 观察列表 | RUNNING 态正确 | 无 |
| D-15-10 | 无权限访问 | 权限 | 无 backup 权限 | 访问 | 守卫拦截 | L1 |

### D16 版本管理（/system/version）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-16-1 | 版本 CRUD | 功能 | 无 | 增改查 | 成功 | L5-一致性 |
| D-16-2 | semver 格式校验 | 负向 | 无 | 输入 1.2 / v1.2.3 / 1.2.3.4 | /^\d+\.\d+\.\d+$/ 拦截 | L5-一致性 |
| D-16-3 | 重复版本号 409 | 负向 | 已有 1.0.0 | 再建 1.0.0 | 冲突提示 | 无 |
| D-16-4 | releaseDate 必填 | 负向 | 无 | 留空 | required 拦截 | 无 |
| D-16-5 | changelog 非必填 | 边界 | 无 | 仅必填项提交 | 成功 | 无 |
| D-16-6 | 摘要首行截断 | 功能 | changelog 首行>60 字 | 查看列表 | 截断+'…' | 无 |
| D-16-7 | 当前版本 tag | 功能 | 多版本 | 查看列表 | 当前版本标记 | 无 |
| D-16-8 | 查看日志弹窗 | 功能 | 有 changelog | 点查看 | pre 全文展示 | 无 |
| D-16-9 | 非法发布日 | 边界 | 无 | 早于上一版本日期 | 提示或不约束（记录现状） | 无 |

### D17 系统监控（/system/monitor）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-17-1 | 占位页渲染 | 功能 | 无 | 访问页面 | el-empty「待实现（任务 10.3）」 | 无 |
| D-17-2 | 路由可达+权限 | 权限 | 有/无权限 | 访问 /system/monitor | 按 meta.permission 控制 | L1 |
| D-17-3 | monitor API 闲置 | 一致性 | api/monitor.ts 存在 | 检查调用方 | **前端无任何调用**（盲点） | 无 |
| D-17-4 | 实现后 CPU/内存指标 | 功能 | 待实现 | - | 占位用例 | 无 |
| D-17-5 | 实现后 JVM/线程指标 | 功能 | 待实现 | - | 占位用例 | 无 |
| D-17-6 | 实现后磁盘指标 | 功能 | 待实现 | - | 占位用例 | 无 |
| D-17-7 | 实现后刷新轮询 | 功能 | 待实现 | - | 占位用例 | 无 |
| D-17-8 | 实现后接口异常降级 | 负向 | 待实现 | - | 占位用例 | 无 |

### D-X2 系统管理跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| D-17-9 | 角色权限变更→菜单/按钮实时生效 | 集成 | 取消角色某菜单权限→该角色用户重登/刷新 | 侧边菜单与 v-permission 按钮同步消失 | L1(组件级) |
| D-17-10 | 菜单删除→角色权限树同步 | 集成 | 删除菜单后打开角色权限树 | 树不再含该节点，已授权不残留 | 无 |
| D-17-11 | 重置密码→新密码登录 | 集成 | 管理端重置密码 | 旧密码失败、新密码登录成功 | 无 |
| D-17-12 | 编号规则→业务单据编号 | 集成 | 改规则后创建业务单据 | 单据编号按新前缀/补零生成 | 无 |

## D-3 基础数据（/basedata，7 页）

**业务概述**：7 页均为精简 CRUD（材料/供应商/甲方/自持公司/检查方案/供应商评价/黑名单）。源码实证差距：supplier-evaluation.vue 仅单一 score（input-number min=1 max=100，只有新增+删除无编辑），**无五维评分**；inspection-scheme.vue 仅方案级字段（QUALITY/SAFETY + ENABLED/DISABLED），**无检查项模板维护 UI**。

### D18 材料字典（/basedata/material）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-18-1 | 材料 CRUD | 功能 | 无 | 增改查删 | 成功 | L5-API/L5-一致性 |
| D-18-2 | materialName 必填 | 负向 | 无 | 留空提交 | required 拦截 | L5-一致性 |
| D-18-3 | referencePrice 精度 | 边界 | 无 | 输入 1.234 / 负数 | precision=2、min=0 钳制 | 无 |
| D-18-4 | 材料编码重复 | 负向 | 已有编码 | 新增同码 | 冲突提示 | 无 |
| D-18-5 | 分类关联 | 功能 | 分类树存在 | 选分类建材料 | /material/categories 数据正确 | L5-API |
| D-18-6 | 名称查询+分页 | 功能 | 数据充足 | 查询翻页 | 正确 | L5-API |
| D-18-7 | 删除被引用材料 | 边界 | 材料被单据引用 | 删除 | 后端约束提示（reference-check） | L1(reference-check-dialog) |
| D-18-8 | 无权限隐藏新增 | 权限 | 无权限 | 访问 | 按钮 v-permission 隐藏 | L1 |

### D19 供应商（/basedata/supplier）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-19-1 | 供应商 CRUD | 功能 | 无 | 增改查删 | 成功 | L5-API/L5-一致性 |
| D-19-2 | supplierName 必填 | 负向 | 无 | 留空 | required 拦截 | L5-一致性 |
| D-19-3 | 类型三选查询 | 功能 | 三类数据 | 按 MATERIAL/MACHINE/LABOR 筛选 | 结果正确 | 无 |
| D-19-4 | 表单类型字段提交 | 功能 | 无 | 新增带类型 | 类型持久化 | 无 |
| D-19-5 | 名称重复 | 负向 | 同名存在 | 新增同名 | 冲突提示 | 无 |
| D-19-6 | 黑名单供应商标识联动 | 集成 | 供应商在黑名单 | 查看列表/选择 | 状态可见或受限（依源码现状记录） | 无 |
| D-19-7 | 分页翻页 | 功能 | 数据充足 | 翻页 | 正确 | L5-API |
| D-19-8 | 删除确认 | 负向 | 供应商存在 | 删除→取消 | 不删除 | L5-API |
| D-19-9 | 无权限访问 | 权限 | 无权限 | 访问 | 守卫拦截 | L1 |

### D20 甲方单位（/basedata/owner）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-20-1 | 甲方 CRUD | 功能 | 无 | 增改查删 | 成功 | L5-API/L5-一致性 |
| D-20-2 | ownerName 必填 | 负向 | 无 | 留空 | required 拦截 | L5-一致性 |
| D-20-3 | 下拉列表接口 | 功能 | 有数据 | /owner/list | 数组返回 | L5-API |
| D-20-4 | 名称重复 | 负向 | 同名存在 | 新增 | 冲突提示 | 无 |
| D-20-5 | 联系人/电话字段 | 功能 | 无 | 填写更新 | 持久化 | L5-API |
| D-20-6 | 名称查询 | 功能 | 数据充足 | 查询 | 正确 | L5-API |
| D-20-7 | 删除被项目引用甲方 | 边界 | 甲方被项目引用 | 删除 | 约束提示 | L1(reference-check-dialog) |
| D-20-8 | 无权限访问 | 权限 | 无权限 | 访问 | 守卫拦截 | L1 |

### D21 自持公司（/basedata/company）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-21-1 | 公司 CRUD | 功能 | 无 | 增改查删 | 成功 | L5-API/L5-一致性 |
| D-21-2 | companyName 必填 | 负向 | 无 | 留空 | required 拦截 | L5-一致性 |
| D-21-3 | 名称重复 | 负向 | 同名存在 | 新增 | 冲突提示 | 无 |
| D-21-4 | 查询+分页 | 功能 | 数据充足 | 查询翻页 | 正确 | L5-API |
| D-21-5 | 编辑回显 | 功能 | 公司存在 | 点编辑 | 表单回填完整 | L5-一致性 |
| D-21-6 | 删除确认 | 负向 | 公司存在 | 删除→取消 | 不删除 | L5-API |
| D-21-7 | 删除被引用公司 | 边界 | 被业务引用 | 删除 | 约束提示 | L1(reference-check-dialog) |
| D-21-8 | 无权限访问 | 权限 | 无权限 | 访问 | 守卫拦截 | L1 |

### D22 检查方案（/basedata/inspection-scheme）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-22-1 | 方案 CRUD | 功能 | 无 | 增改查删 | 成功 | L5-一致性 |
| D-22-2 | schemeName/schemeType 必填 | 负向 | 无 | 留空 | required 拦截 | L5-一致性 |
| D-22-3 | 类型 QUALITY/SAFETY | 功能 | 无 | 两类分别创建查询 | 过滤正确 | 无 |
| D-22-4 | 启用/停用切换 | 功能 | 方案存在 | 切换 ENABLED/DISABLED | 状态更新 | 无 |
| D-22-5 | 检查项模板维护 | 负向 | 背景需求 | 尝试维护检查项 | **源码无检查项 UI**（差距，不可执行） | 无 |
| D-22-6 | 停用方案不可被现场引用 | 集成 | 方案停用 | 现场端选择方案 | 不可选（依后端规则） | 无 |
| D-22-7 | 名称重复 | 负向 | 同名存在 | 新增 | 冲突提示 | 无 |
| D-22-8 | 查询+分页 | 功能 | 数据充足 | 查询翻页 | 正确 | L5-一致性 |

### D23 供应商评价（/basedata/supplier-evaluation）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-23-1 | 新增评价 | 功能 | 供应商存在 | 选供应商+评分提交 | 成功 | L5-一致性 |
| D-23-2 | supplierName/score 必填 | 负向 | 无 | 留空 | required 拦截 | L5-一致性 |
| D-23-3 | score 边界 1/100 | 边界 | 无 | 输入 0/101 | min=1 max=100 钳制 | 无 |
| D-23-4 | 无编辑入口 | 边界 | 评价存在 | 查看操作列 | 仅新增+删除（源码现状） | 无 |
| D-23-5 | 删除评价确认 | 负向 | 评价存在 | 删除 | confirm 后删除 | 无 |
| D-23-6 | 五维评分 | 负向 | 背景需求 | 尝试分维度评分 | **源码仅单一 score**（差距，不可执行） | 无 |
| D-23-7 | 分页接口兼容 | 一致性 | 有数据 | 分页查询 | 根路径 GET 分页（无 /page 别名） | L5-API(200/404) |
| D-23-8 | 同供应商重复评价 | 边界 | 已有评价 | 再评同一供应商 | 允许多条或约束（记录现状） | 无 |
| D-23-9 | 低分评价联动黑名单 | 集成 | 低分 | 查看供应商状态 | 提示或人工转黑名单（现状记录） | 无 |

### D24 供应商黑名单（/basedata/supplier-blacklist）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-24-1 | 新增黑名单 | 功能 | 供应商存在 | 填写原因提交 | 成功 | L5-一致性 |
| D-24-2 | supplierName/reason 必填 | 负向 | 无 | 留空 | required 拦截 | L5-一致性 |
| D-24-3 | 移出黑名单确认 | 负向 | 黑名单存在 | 移出→confirm | 确认后移出；取消不生效 | 无 |
| D-24-4 | 重复拉黑 | 负向 | 已在黑名单 | 再次拉黑 | 冲突/重复提示 | 无 |
| D-24-5 | 分页接口兼容 | 一致性 | 有数据 | 分页查询 | 根路径 GET（无 /page 别名） | L5-API(200/404) |
| D-24-6 | 黑名单供应商选择拦截 | 集成 | 采购/分包选择供应商 | 选择黑名单供应商 | 不可选或警示 | 无 |
| D-24-7 | 原因超长输入 | 边界 | 无 | reason 超长 | 按约束处理 | 无 |
| D-24-8 | 无权限访问 | 权限 | 无权限 | 访问 | 守卫拦截 | L1 |

### D-X3 基础数据跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| D-24-9 | 材料字典→支出域选材 | 集成 | 材料入库后在支出单据选择 | 下拉含新材料且单价带出 | 无 |
| D-24-10 | 供应商类型→对应支出模块可选范围 | 集成 | LABOR 供应商 | 仅劳务模块可选 | 无 |
| D-24-11 | 字典(basedata 相关)→useDict 渲染 | 集成 | 修改字典值 | 各页下拉同步 | L1(use-dict) |

## D-4 消息管理（/message，4 页）

**业务概述**：通知（title/content required，发布按钮 :disabled="row.status==='PUBLISHED'"，无编辑/删除）、公告（DRAFT→PUBLISHED→REVOKED 状态机，按钮 disabled 规则严格按状态）、推送配置（businessType 必填且**编辑时 disabled**、四渠道 switch、模板下拉 v-show 联动，分页用 page/size）、消息中心（未读/全部 tab、badge max=99、异地登录 LOGIN_LOCATION/SECURITY 特殊渲染并跳转 /user/devices）。

### D25 通知管理（/message/notice）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-25-1 | 新增通知 | 功能 | 无 | 填 title/content 提交 | DRAFT 创建 | L5-一致性 |
| D-25-2 | title/content 必填 | 负向 | 无 | 留空 | required 拦截 | L5-一致性 |
| D-25-3 | 发布通知 | 功能 | DRAFT 通知 | 发布→confirm | 状态变 PUBLISHED | 无 |
| D-25-4 | 已发布禁再发布 | 边界 | PUBLISHED | 查看发布按钮 | disabled(row.status==='PUBLISHED') | 无 |
| D-25-5 | 无编辑/删除入口 | 边界 | 任意通知 | 查看操作列 | 源码仅新增+发布（现状记录） | 无 |
| D-25-6 | 发布取消 | 负向 | DRAFT | confirm 取消 | 状态不变 | 无 |
| D-25-7 | 标题超长 | 边界 | 无 | 超长 title | 按约束处理 | 无 |
| D-25-8 | 发布后消息中心触达 | 集成 | 发布通知 | 用户消息中心 | 出现对应消息 | 无 |
| D-25-9 | 无权限隐藏发布按钮 | 权限 | 无权限 | 访问 | v-permission 隐藏 | L1 |

### D26 公告管理（/message/announcement）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-26-1 | 新增公告 | 功能 | 无 | 填 title/content/scope | DRAFT 创建 | L5-一致性 |
| D-26-2 | 三字段必填 | 负向 | 无 | 逐项留空 | required 拦截 | L5-一致性 |
| D-26-3 | 发布→PUBLISHED | 功能 | DRAFT | 发布 | 状态与 tag 更新（statusLabel/statusTagType map） | 无 |
| D-26-4 | 撤回→REVOKED | 功能 | PUBLISHED | 撤回 | 状态 REVOKED | 无 |
| D-26-5 | 已发布禁编辑/发布/删除 | 边界 | PUBLISHED | 查看按钮 | 三按钮 disabled | 无 |
| D-26-6 | 非已发布禁撤回 | 边界 | DRAFT/REVOKED | 查看撤回按钮 | disabled | 无 |
| D-26-7 | scope ALL/DEPARTMENT | 功能 | 无 | 两种范围创建 | 持久化正确 | 无 |
| D-26-8 | isTop 置顶 | 功能 | 公告存在 | 开置顶 | 列表排序优先 | 无 |
| D-26-9 | REVOKED 再发布 | 边界 | REVOKED | 发布 | 按源码 disabled 规则允许/拒绝（记录现状） | 无 |
| D-26-10 | 删除 DRAFT 确认 | 负向 | DRAFT | 删除→取消 | 不删除 | 无 |
| D-26-11 | 公告全员可见性 | 集成 | scope=ALL 发布 | 普通用户查看 | 可见 | 无 |

### D27 推送配置（/message/push-config）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-27-1 | 新增配置 | 功能 | 无 | 填 businessType/TypeName+渠道 | 成功 | L5-一致性 |
| D-27-2 | businessType/TypeName 必填 | 负向 | 无 | 留空 | required 拦截 | L5-一致性 |
| D-27-3 | 编辑时业务类型锁定 | 功能 | 配置存在 | 点编辑 | businessType disabled(:disabled="isEdit") | 无 |
| D-27-4 | 业务类型唯一性 409 | 负向 | 已有配置 | 新建同 businessType | 唯一性冲突提示 | 无 |
| D-27-5 | 四渠道 switch | 功能 | 无 | 逐渠道开关 | 状态持久化 | 无 |
| D-27-6 | 模板下拉联动 | 功能 | 渠道开启 | 选模板 | v-show 联动，loadTemplates(page:1,size:200) | 无 |
| D-27-7 | 分页参数 page/size | 一致性 | 数据充足 | 翻页 | 请求用 page/size（与全局 pageNum/pageSize 不一致） | 无 |
| D-27-8 | 删除配置确认 | 负向 | 配置存在 | 删除 | confirm 后删除 | 无 |
| D-27-9 | 全渠道关闭保存 | 边界 | 无 | 四渠道全关提交 | 允许保存或提示（记录现状） | 无 |
| D-27-10 | 无权限访问 | 权限 | 无权限 | 访问 | 守卫拦截 | L1 |

### D28 消息中心（/message/center）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-28-1 | 未读/全部 tab | 功能 | 有消息 | 切 tab | 列表过滤正确 | L5-一致性 |
| D-28-2 | badge max=99 | 边界 | 未读>99 | 查看角标 | 显示 99+ | 无 |
| D-28-3 | 标记已读 | 功能 | 未读消息 | 点已读 | isRead 更新，按钮转 disabled(row.isRead) | 无 |
| D-28-4 | 已读禁再标记 | 边界 | 已读消息 | 查看按钮 | disabled | 无 |
| D-28-5 | 全部已读 | 功能 | 有未读 | 点全部已读 | 批量标记 | 无 |
| D-28-6 | 无未读禁全部已读 | 边界 | unreadCount===0 | 查看按钮 | disabled | 无 |
| D-28-7 | 异地登录特殊渲染 | 功能 | LOGIN_LOCATION/SECURITY 消息 | 查看列表 | 安全提醒 tag+行高亮 | 无 |
| D-28-8 | 查看设备跳转 | 集成 | 异地登录消息 | 点查看设备 | router.push('/user/devices') | 无 |
| D-28-9 | 快捷入口批量保存 | 功能 | 背景提及 | 配置快捷入口 | 批量保存生效（按源码现状） | 无 |
| D-28-10 | 空消息占位 | 边界 | 无消息 | 打开 | 空态展示 | 无 |

### D-X4 消息跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| D-28-11 | 推送配置→业务事件触达 | 集成 | 配置渠道后触发审批事件 | 按渠道路由推送 | 无 |
| D-28-12 | 模板管理→推送配置下拉 | 集成 | 新建通用模板 | push-config 模板下拉出现 | 无 |
| D-28-13 | 多设备踢出→安全消息→设备页 | 集成 | 第 6 设备登录 | 被踢用户收到 SECURITY 消息并可跳设备页 | 无 |

## D-5 工作流管理（/workflow，5 页）

**业务概述**：设计器集成 bpmn-js（DEFAULT_XML：StartEvent→UserTask→EndEvent，导入失败提示「导入的文件格式不正确」，unmount 时 destroy）；流程定义页支持部署上传（accept .bpmn/.bpmn20.xml/.xml）、流程图与历史版本，但**无挂起/激活**；审批页仅 todo/done 两 tab（批量通过、通过附意见、退回 previous/start、终止），**无「我发起」/委托 UI/撤回**——api/workflow.ts 亦无 suspend/activate/withdraw 函数（delegateTask/transferTask API 存在但无页面消费）。

### D29 流程设计器（/workflow/designer）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-29-1 | 画布初始化 | 功能 | 无 | 进入页面 | BpmnModeler 加载 DEFAULT_XML 三节点 | L5-UI |
| D-29-2 | 导出 XML 下载 | 功能 | 画布有内容 | 保存/下载 | 下载 process.bpmn（saveXML） | 无 |
| D-29-3 | 导入合法 bpmn | 功能 | 有效文件 | 导入 | importXML 成功渲染 | 无 |
| D-29-4 | 导入非法文件 | 负向 | 非 bpmn 文件 | 导入 | 提示「导入的文件格式不正确」 | 无 |
| D-29-5 | 部署流程 | 集成 | 画布有效 | 部署 | FormData(file+name) 提交成功 | 无 |
| D-29-6 | 部署空画布 | 负向 | 未改动 | 部署 | 默认 XML 可部署或提示 | 无 |
| D-29-7 | 组件销毁释放 | 边界 | 画布已加载 | 离开页面 | onBeforeUnmount modeler.destroy() 无泄漏 | 无 |
| D-29-8 | 拖拽节点编辑 | 功能 | 无 | 增删节点连线 | XML 同步变化 | 无 |
| D-29-9 | 无权限访问 | 权限 | 无权限 | 访问 | 守卫拦截 | L1 |

### D30 流程定义（/workflow/process）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-30-1 | 定义列表 | 功能 | 已部署流程 | 进入页面 | getProcessList 渲染 | L5-API/L5-一致性/L5-UI |
| D-30-2 | 上传部署 | 功能 | 合法 .bpmn | 上传 | 部署成功列表+版本 | L5-API |
| D-30-3 | 上传格式限制 | 负向 | .txt 文件 | 上传 | accept 限制/拒绝 | 无 |
| D-30-4 | 流程图展示 | 功能 | 定义存在 | 查看图片 | getProcessImage URL 渲染 | 无 |
| D-30-5 | 图片加载失败占位 | 边界 | URL 失效 | 查看 | 占位图 | 无 |
| D-30-6 | 历史版本查看 | 功能 | 同 key 多版本 | 点版本 | getProcessVersions(row.key) 列表 | 无 |
| D-30-7 | 挂起/激活 | 负向 | 背景需求 | 查找按钮 | **源码无按钮，api 无 suspend/activate**（差距） | 无 |
| D-30-8 | 重复部署同 key | 边界 | 已部署 | 再传同 key | 版本+1 而非报错 | 无 |
| D-30-9 | 无权限访问 | 权限 | 无权限 | 访问 | 守卫拦截 | L1 |

### D31 业务类型（/workflow/business-type）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-31-1 | 类型树 CRUD | 功能 | 无 | 增改删 | 成功 | L5-API |
| D-31-2 | typeName/typeCode 必填 | 负向 | 无 | 留空 | required 拦截 | 无 |
| D-31-3 | 绑定流程 processKey | 功能 | 已部署流程 | 选 processKey 保存 | 详情显示已关联 | 无 |
| D-31-4 | 未关联显示 | 边界 | 未绑定 | 查看详情 | 显示「未关联」 | 无 |
| D-31-5 | 树过滤 | 功能 | 多节点 | 输入关键字 | filter 生效 | 无 |
| D-31-6 | 新增子类型 | 功能 | 选中父节点 | 新增子级 | 树结构更新 | 无 |
| D-31-7 | 详情接口失败降级 | 边界 | 详情接口异常 | 点节点 | 降级用树节点数据渲染 | 无 |
| D-31-8 | typeCode 重复 | 负向 | 同码存在 | 新增 | 冲突提示 | 无 |
| D-31-9 | 绑定→审批路由生效 | 集成 | 绑定后提交业务单 | 发起审批 | 走所绑流程（18 有合同触发用例） | L5-API |

### D32 审批管理（/workflow/approval）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-32-1 | 待办列表 | 功能 | 有待办 | 进 todo tab | getTodoTasks 渲染 | L5-API/L5-一致性/L5-UI |
| D-32-2 | 已办列表 | 功能 | 有已办 | 切 done tab | getDoneTasks 渲染 | 同上 |
| D-32-3 | 单任务通过附意见 | 功能 | 有待办 | 通过弹窗填 comment 确认 | completeTask 成功，待办减少 | L1(use-approval)/L5-UI |
| D-32-4 | 退回到上一节点 | 功能 | 多节点流程 | 退回弹窗选 previous | rejectToPrevious | 无 |
| D-32-5 | 退回到发起人 | 功能 | 待办 | 选 start | rejectToStart | 无 |
| D-32-6 | 批量通过 | 功能 | 勾选多待办 | 批量通过→confirm | batchApprove(taskIds) | L5-API |
| D-32-7 | 批量通过空选 | 边界 | 未勾选 | 点批量通过 | 禁用或提示 | 无 |
| D-32-8 | 终止流程 | 功能 | 进行中实例 | 终止→confirm「终止后不可恢复」 | terminateProcess | 无 |
| D-32-9 | 已被他人办理的任务 | 边界 | 任务已被处理 | 再通过 | 后端冲突提示，前端不崩 | 无 |
| D-32-10 | 委托/转办 UI | 负向 | 背景需求 | 查找入口 | **无 UI**（api 有 delegateTask/transferTask，差距） | 无 |
| D-32-11 | 我发起/撤回 | 负向 | 背景需求 | 查找 tab | **无第三 tab、无 withdraw**（api 亦无，差距） | 无 |
| D-32-12 | 无权限访问 | 权限 | 无权限 | 访问 | 守卫拦截 | L1 |

### D33 审批回滚日志（/workflow/rollback）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-33-1 | 日志列表 | 功能 | 有回滚记录 | 进入页面 | getRollbackLogs 渲染 | L5-一致性 |
| D-33-2 | bizType 6 选项过滤 | 功能 | 多类型记录 | 逐类型筛选 | 过滤正确 | 无 |
| D-33-3 | 状态四态渲染 | 功能 | 四种状态记录 | 查看 | 0成功/1失败/2冲突待确认/3重试中正确 | 无 |
| D-33-4 | 冲突处理入口可见性 | 功能 | status===2 与非2 记录 | 查看操作列 | 仅 status===2 显示「处理冲突」 | 无 |
| D-33-5 | 冲突处理必选方案 | 负向 | 冲突记录 | 不选 resolution 确认 | 按钮 disabled(:disabled="!conflictForm.resolution") | 无 |
| D-33-6 | 三种 resolution 提交 | 功能 | 冲突记录 | 分别 FORCE_ROLLBACK/SKIP/MANUAL | confirmRollbackConflict 成功 | 无 |
| D-33-7 | 日期范围查询 | 功能 | 有记录 | 选 dateRange | startDate/endDate 传参正确 | 无 |
| D-33-8 | 分页参数 page/size | 一致性 | 数据充足 | 翻页 | 用 page/size | 无 |
| D-33-9 | 冲突处理后状态流转 | 集成 | 处理冲突 | 刷新 | 状态变 0/3 | 无 |
| D-33-10 | 无权限访问 | 权限 | 无权限 | 访问 | 守卫拦截 | L1 |

### D-X5 工作流跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| D-33-11 | 设计器部署→定义列表→发起审批全链 | 集成 | 设计→部署→业务单提交→审批通过 | 各页状态一致流转 | L5-API/L5-UI |
| D-33-12 | 审批通过→业务单据状态回写 | 集成 | 通过合同审批 | 合同页状态变更 | L5-UI(相关) |
| D-33-13 | 业务类型改绑流程→新单据路由 | 集成 | 更换 processKey 后提交 | 新单据走新流程 | 无 |
| D-33-14 | 审批事件→消息中心 | 集成 | 待办产生 | 消息中心出现待办提醒 | 无 |

## D-6 平台管理（/platform，超管专属，3 页）

**业务概述**：租户管理（6 字段全 required、maxUsers 1-9999、durationDays 1-3650、停用仅 status===1 且 confirm 明示「停用后该租户下所有用户将无法登录」、续期 1-1095、12 功能模块 checkbox）；租户类型（durationDays 固定 select 30/90/180/365，分页 page/size）；存储配置（**仅 storageType required，endpoint/accessKey/secretKey/bucket/basePath 均无必填校验**，secretKey 为 password 型，API 走 @/api/file）。

### D34 租户管理（/platform/tenant）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-34-1 | 租户 CRUD | 功能 | 超管 | 增改查 | 成功 | L5-一致性 |
| D-34-2 | 6 字段全必填 | 负向 | 超管 | 逐项留空 | required 拦截 | L5-一致性 |
| D-34-3 | maxUsers 边界 | 边界 | 无 | 输入 0/10000 | min=1 max=9999 钳制 | 无 |
| D-34-4 | durationDays 边界 | 边界 | 无 | 输入 0/3651 | 1-3650 钳制 | 无 |
| D-34-5 | 状态/类型组合查询 | 功能 | 多租户 | status(1/2/3)×userType 筛选 | 过滤正确 | 无 |
| D-34-6 | 停用租户 | 功能 | status===1 | 停用→confirm | 提示含「所有用户将无法登录」，状态→2 | 无 |
| D-34-7 | 非正常态无停用按钮 | 边界 | status 2/3 | 查看操作列 | 停用隐藏，启用显示(v-if) | 无 |
| D-34-8 | 续期天数校验 | 负向 | 租户存在 | 输入 0/1096 | renewRules min1 max1095 拦截 | 无 |
| D-34-9 | 续期成功延长到期日 | 功能 | 租户存在 | 续期 30 天 | endDate 顺延 | 无 |
| D-34-10 | 模块配置 12 项保存 | 功能 | 租户存在 | 勾选模块保存 | updateTenantModules 生效 | 无 |
| D-34-11 | 使用量展示 | 功能 | currentUsers 存在 | 查看列表 | currentUsers/maxUsers 渲染 | 无 |
| D-34-12 | 非超管访问平台页 | 权限 | 普通用户 | 访问 /platform/tenant | 守卫/菜单拦截 | L1(超管 *:*:* 绕过) |

### D35 用户类型（/platform/tenant-type）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-35-1 | 类型 CRUD | 功能 | 超管 | 增改查删 | 成功 | L5-一致性 |
| D-35-2 | typeName/durationDays 必填 | 负向 | 无 | 留空 | required 拦截 | L5-一致性 |
| D-35-3 | durationDays 固定四档 | 功能 | 无 | 选 30/90/180/365 | select 限定 | 无 |
| D-35-4 | sortOrder 边界 | 边界 | 无 | 输入负数 | min=0 钳制 | 无 |
| D-35-5 | status switch | 功能 | 类型存在 | 启停切换 | 持久化 | 无 |
| D-35-6 | 删除确认 | 负向 | 类型存在 | 删除→取消 | 不删除 | 无 |
| D-35-7 | 分页 page/size | 一致性 | 数据充足 | 翻页 | 请求用 page/size | 无 |
| D-35-8 | 删除被租户引用类型 | 边界 | 类型被租户绑定 | 删除 | 约束提示 | 无 |
| D-35-9 | 非超管访问 | 权限 | 普通用户 | 访问 | 拦截 | L1 |

### D36 存储配置（/platform/storage）

| 用例ID | 测试点 | 类型 | 前置条件 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|---|
| D-36-1 | 保存 MinIO 配置 | 功能 | 超管 | 选 MINIO 填全参数 | 保存成功（@/api/file） | L5-一致性 |
| D-36-2 | storageType 必填 | 负向 | 无 | 留空 | required 拦截 | L5-一致性 |
| D-36-3 | 五种存储类型切换 | 功能 | 无 | LOCAL/MINIO/ALIYUN/TENCENT/QINIU | 表单适配 | 无 |
| D-36-4 | 空 endpoint 保存 | 负向 | 选 MINIO | 仅填 storageType 提交 | **前端无必填校验直接提交**，依赖后端（盲点） | 无 |
| D-36-5 | secretKey 掩码 | 功能 | 无 | 输入密钥 | type=password 掩码显示 | 无 |
| D-36-6 | 连接测试/生效验证 | 集成 | 配置保存 | 上传文件 | 按新存储落盘 | 无 |
| D-36-7 | 非法 endpoint | 负向 | 无 | 输入非法 URL | 后端报错前端提示 | 无 |
| D-36-8 | 非超管访问 | 权限 | 普通用户 | 访问 | 拦截 | L1 |

### D-X6 平台跨模块集成

| 用例ID | 测试点 | 类型 | 操作步骤 | 预期结果 | 现有覆盖 |
|---|---|---|---|---|---|
| D-36-9 | 租户停用→全员登录拦截 | 集成 | 停用租户 | 租户用户登录/续会话 | 登录被拒、现有会话失效 | 无 |
| D-36-10 | 租户模块配置→菜单可见性 | 集成 | 关闭 FINANCE 模块 | 租户用户登录 | 对应模块菜单不可见 | 无 |
| D-36-11 | maxUsers 上限→新增用户拦截 | 集成 | currentUsers=maxUsers | 租户内新建用户 | 超限拒绝 | 无 |
| D-36-12 | 存储配置→备份/模板文件链路 | 集成 | 切换存储后手动备份 | 备份文件下载 | 经新存储可读 | 无 |

---

# 附录一：源码级测试盲点汇总（四组实证 Top 项去重合并）

| # | 盲点 | 模块 | 证据 |
|---|---|---|---|
| 1 | **付款申请多合同类型路由零覆盖**：PURCHASE/LABOR/MACHINE/SUBCONTRACT/OTHER_EXPENSE 五条合同加载路径+类型切换清空逻辑无任何层级测试；审批通过→合同累计付款→totalExpense 核心资金链无集成验证 | 财务 | payment-apply.vue loadContracts switch 五分支 |
| 2 | **工作流四大能力前端整体缺失**：无挂起/激活、无「我发起」/撤回/委托 UI（api 部分存在无消费方），背景需求与实现脱节且零覆盖 | 工作流 | process/approval/index.vue + api/workflow.ts |
| 3 | **合同「查看」无只读视图**：handleView 与 handleEdit 同跳编辑页，非草稿亦可进编辑表单尝试保存 | 合同 | contract/index.vue L170-172 |
| 4 | **BOQ 上传解析零覆盖 + 大小写边界缺陷**：endsWith('.xlsx') 误拒 .XLSX；upload/buildTree/统计全链无测试 | 合同 | boq-upload.vue L183 |
| 5 | **产值上报写路径零覆盖 + 既有测试路径失配**：06-contract.spec.ts 请求 /v1/contract/output/page 而实际 API 为 /v1/contract/output 且断言容忍 404，等效未覆盖 | 合同 | output-report.vue + api/contract.ts |
| 6 | **封账拦截链路断裂**：getLockStatus API 已定义但无 view 引用，单据页不做封账前置检查，拦截完全依赖后端报错 | 财务 | finance-lock/index.vue + 各财务单据页 |
| 7 | **检查方案驱动机制零覆盖**：方案填充/applyScheme/快照恢复/只读禁增/100 条上限/空行 filter 六个源码显式逻辑全部无测试 | 现场 | inspection/form.vue |
| 8 | **图表数据绑定全空白 + 测试资产过期**：HR 统计 4 图、首页饼/柱图、项目看板 4 渲染函数无断言；consistency/dashboard.spec.ts 仍将已修复的 __silentFallback__ 当固定缺陷写入报告 | 看板 | dashboard/index.vue + consistency spec |
| 9 | **薪资统计「工人姓名」筛选完全失效**：控件存在但既不传 API 也不本地过滤 | 劳务 | salary/stats.vue L21-23/L227-244/L282-283 |
| 10 | **入库单编辑模式绕过明细必填守卫**：仅 !isEdit 时校验，编辑可保存空明细 | 材料 | inbound.vue L187 |
| 11 | **调拨同项目仅前端守卫，后端实测放行**：10-material.spec.ts 直接 from=to 创建成功；出库数量前端不比对库存 | 材料 | transfer.vue L170 + outbound.vue |
| 12 | **机械结算 create 无空预览/重复周期守卫**：可创建 0 元结算单 | 机械 | settlement/create.vue L142-156 |
| 13 | **四个支出合同页 UI 均无提交审批按钮但 API 存在**（机械/劳务/分包/采购），合同生效链路 UI 断链 | 支出域 | 四个 contract.vue + api submit 函数 |
| 14 | **企业证书前端不可达**：row.type 恒缺省恒走 person 分支，company 只能 API 直调 | 投标 | certificate.vue L96 |
| 15 | **通用模板文件上传为假实现**：handleFileChange 仅 ElMessage.info，fileId 恒 null，IMPORT 模板链路断裂 | 系统 | template/index.vue |
| 16 | **监控页纯占位但 api/monitor.ts 已闲置**：死代码+零覆盖双重盲点 | 系统 | monitor/index.vue + api/monitor.ts |
| 17 | **基础数据背景能力缺失**：供应商评价无五维评分（仅单一 score 且无编辑）、检查方案无检查项模板 UI、日志无批删、登录无记住用户名——四处「背景提及、源码缺失」需产品确认 | 基础数据/登录 | 四个 view 源码实证 |
| 18 | **契约不一致与安全链路端到端缺口**：分页参数两套并存（page/size vs pageNum/pageSize）；storage 除 storageType 外 5 字段无必填校验；验证码一次性消费/IP 锁定/多设备踢出三个安全机制前端零用例 | 全局 | 多处实证 |
| 19 | **预算管控语义与变更审批链无端到端验证**：submit/withdraw/getEffectiveConfig 及 BLOCK/WARN_ONLY/EXEMPT 拦截语义无测试；budget 编辑按钮不受状态限制、tender 编辑对 WON/LOST 可入 | 预算/投标 | budget/index.vue + register.vue |
| 20 | **项目详情 statusMap 缺 CLOSING**：结项审批中状态回退显示原始枚举串；tender/register 分页参数与全局不一致 | 项目/投标 | detail.vue + register.vue |

---

# 附录二：落地实施优先级建议

> 原则：先补「核心资金链 + 安全机制 + 已实证缺陷回归」，再补交互细节；层级选择上，组件/逻辑层优先用 L1 单测（成本低、可入 push 门禁），跨模块链路用 L5-API/consistency 扩展。

| 优先级 | 批次 | 内容 | 建议层级 | 预估用例 |
|---|---|---|---|---|
| P0 | 资金链 | C-5 付款申请五分支路由 + C-FIN-X1/X2/X5 回写勾稽 + C-13 封账拦截闭环（C-FIN-X4） | L5-API + consistency 扩展 | ~35 |
| P0 | 安全机制 | D-1-5/D-1-6/D-3-9 验证码一次性/IP 锁定/多设备踢出 + D-2 忘记密码全流程（当前零覆盖） | L5-API + L5-UI | ~22 |
| P0 | 缺陷回归 | 盲点 3/4/5（合同查看/BOQ/产值）+ 盲点 9/10/11（劳务/材料守卫）——先修源码缺陷再补钉住用例 | L1 组件 + L5-API | ~30 |
| P1 | 预算管控 | A-14 BLOCK/WARN_ONLY/EXEMPT 端到端 + A-X15∼19 变更审批链 | L5-API（租户 9999） | ~15 |
| P1 | 审批主链 | D-32 退回/终止/批量 + D-33-11∼14 跨模块回写（补齐 L5-UI 仅 3 页面缺口） | L5-UI 扩展 | ~18 |
| P1 | 检查方案 | C-18 方案驱动 6 逻辑 + C-SITE-X2 整改闭环 | L1 组件 + L5-API | ~16 |
| P2 | 图表绑定 | C-20/C-29/C-30 数值变换与数据绑定（含修复 consistency/dashboard 过期断言） | L1 组件 | ~25 |
| P2 | 支出域补齐 | B 组写路径（结算/工资单/询价定标 UI 层）+ 盲点 13 合同提交入口决策后补 | L5-UI | ~40 |
| P3 | CRUD 长尾 | 剩余「无」覆盖的功能/边界用例按模块渐进补齐，boy-scout 规则驱动 | L1 + L5 | 剩余 ~600 |

**配套机制**：①每批补测后更新 tests/frontend-coverage-baseline.json（只升不降自然驱动）；②盲点 17 类「背景提及、源码缺失」项先产品确认再定「补功能」或「改文档」；③盲点中的源码缺陷（如 D-11-4 假上传、B-19-8 失效筛选）应走修复流程而非仅测试钉住。

---

*本文档基于 2026-08-14 main 分支源码逐页深读实证生成，四组并行探索（核心主链/支出域/财务现场域/平台支撑域）；用例数字必须可复现，源码变更后应同步更新对应章节。*
