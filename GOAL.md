# GOAL：ZW-Insight 业务全链路闭环 + 资金专项全量测试（核心四层）

## 定稿合同（2026-10-05 用户确认：测试+修复｜线上当前版本｜核心四层）

【最终状态】
主环境（129.204.3.200）线上运行版本的业务全链路测试（L1 单测 + L3 API 契约 + L4 生命周期模拟 + R7 资金数据审计）全部执行完毕且 FAIL=0，资金双口径/勾稽不变量均有断言或审计证据，测试后租户 9999 零残留、审计基线不劣化（PASS≥67），产出带证据链的测试报告。

【验收 — 全部满足且有证据才算完成】
1. L1：`mvn test` 22 模块全绿（0 failure / 0 error）
2. L3：keys/test-api-*.sh（本地全集 28 个脚本，含 fund-loop 资金专项）逐个执行，各脚本 FAIL_COUNT=0
3. L4：`lifecycle-sim-v2.sh` 26 阶段 TOTAL_FAILED=0，且 `verify-l4-clean.sh` 确认 biz_ 表 tenant 9999 残留=0
4. R7 审计 Section 0-7：FAIL=0、PASS≥67；基线变动先判「新失败 vs 新生效」，不得改基线掩盖
5. 资金不变量证据：双口径不回写（pay_status 变更不动 total_expense / cumulative_paid）有断言或审计证据；累计值=单据汇总勾稽由审计 Section 3 覆盖
6. 报告落 audit-reports/，含每层证据摘要、缺陷清单（P0/P1 修复复测记录、P2+ 仅记录）、线上版本信息

【缺陷处置（用户定稿）】R1 先完整跑一轮拿全景；P0/P1 缺陷当轮修复并复测该层全绿；P2 及以下只记入缺陷清单不动代码。

【不变量 — 任何时候不得违反】
- R7 审计全程只读；对生产库（tenant 1）零写入
- L3/L4 写入仅限 tenant 9999；清理仅限 biz_% 表；不直接删 ACT_ 表
- 不在生产服务器构建镜像、不重启/停止生产容器
- 禁止削弱断言、放宽阈值、跳过测试制造达标假象；禁止无依据 sleep/retry
- 测试受阻走 AGENTS.md 受阻汇报规则（登记 tasks.md 受阻表 + 上报三选项），禁止静默降级
- 目标外问题只记录到下方备注，不顺手修

【边界】
允许：执行 keys/、tests/ 既有脚本；SSH 上传脚本到 /root/zwi-deploy 并执行；tenant 9999 全套业务操作（含 init-test-tenant、deploy-bpmn 前置）；本地 mvn test（串行，禁止 -T 1C）；读任意代码/文档。只读：生产库数据、种子数据。禁止：未经备份的远程写 SQL；私钥内容外泄。

【迭代策略】每轮：看证据 → 更新假设账本 → 最大区分度最小改动 → 重跑受影响层全套（不只单点）→ 记录轮次。

【停止条件】验收 1-6 全部有证据 → 结束附证据摘要；连续 3 轮无实质进展或真 blocker → 停并输出已试路线/关键证据/最可能原因排序/blocker/所需用户输入。

【已知时点性注意】月度资金计划种子按执行时当月动态取值，跨月后第 8 类预警「无当月计划不判定」是如实行为非缺陷；基线日 2026-09-24 → 2026-10-05 已跨月。

## 假设账本
| # | 假设 | 支持证据 | 反对证据 | 状态 |
|---|------|----------|----------|------|
| 1 | 线上版本（约 10-01 部署）L4 全链路可跑通 | 2026-10-05 实测 26/26 阶段全过，verify-l4-clean 残留 0/0/基建完好/租户1零污染 | 无 | 已验证 |
| 2 | R7 审计基线仍为 PASS=67 FAIL=0 | 2026-10-05 实测 PASS=67 FAIL=0 WARN=0 INFO=39，与基线一致 | 无 | 已验证 |
| 3 | 资金专项 L3（fund-loop）50 断言在当前线上版本全过 | 2026-10-05 实测 60/60（断言已扩至 60） | 初跑 59/60 | 已验证（修复 1 个脚本缺陷后） |
| 4 | fund-loop 断言 21 失败是后端「逾期摊开」回归 | 后端 FundPlanService.java:291 仅首月计逾期、:322-327 覆盖式写入保留历史月快照；2026-09 快照 createdAt=09-30（当时正确） | 无 | 已排除（是脚本跨月时间炸弹，非后端缺陷） |

## 轮次记录
### R1（2026-10-05）— 全景 baseline + 基座缺陷修复
- **L1**：22/22 模块 BUILD SUCCESS（16:44，zw-app 194 tests 0F0E0S）
- **R7 审计**（04:38Z）：PASS=67 FAIL=0 WARN=0 INFO=39，与 2026-09-24 基线一致；Section 3 金额勾稽全 MISMATCH=0
- **L3 资金专项**：fund-loop 初跑 59/60 → FAIL[21] 定性为脚本跨月时间炸弹（`!=当月` 应为 `>当月`；历史月快照按 FundPlanService:322 覆盖式写入语义合法留存）→ 修正断言后 60/60
- **L4**：init-test-tenant 幂等 ✓；BPMN 15+ 定义部署 9999；26/26 阶段全过；verify-l4-clean 四项全过（残留0/Flowable0/基建在/租户1零污染）
- **L3 全集**（27 脚本）：19 绿 + 8 个失败脚本归因：
  - subcontract：仓库内 CRLF 字节（Windows 检出+scp 到 Linux 必炸）→ 本地转 LF + 服务器重传 → 38/0
  - batch2/3/4：`get_token` 只查缓存非空，独立 WORKDIR 里 46 天前过期 token → L0 假 PASS、S0a 401。修复：L0 强制清新登录（rm 缓存再登）→ 20/0、46/0、41/0
  - cost-control：断言用旧契约字段（metrics/categories），现行 DTO 是 totals/categorySummaries → 对齐 → 9/9
  - change-event：脚本腐化 3 处（SITE_DISCOVERY/DESIGN 枚举已废；评估字段 estimatedCostDelta→costDelta 等；状态 PENDING_APPROVAL→APPROVING）+ approve URL 未编码中文被 Tomcat 400 → 逐项对齐后**暴露真后端 P0**（见 R2）
- **验收项5证据链**：PaymentApplyServiceTest:993 `verify(projectMapper, never()).addTotalExpense`（双口径不回写）+ R7 Section 3 勾稽全绿 + fund-loop[19-21] 现金口径消费侧

### R2（2026-10-05）— 后端 P0 修复（本地验证完成，待部署复测）
- **P0 缺陷**：`ChangeEventService.validateCostDetails:495` ClassCastException——MyBatis-Plus `JacksonTypeHandler` 按裸 List 反序列化 JSON 列丢元素泛型，`List<AffectedAccount>` 读回实为 `List<LinkedHashMap>`；**凡带成本明细（costDelta≠0 时强制要求）的变更事件提交评估必 500**，变更→成本传导主链（ChangeEventApprovalHandler→CBS current_amount）在线上完全不可用。单测 mock 实体类型正确故从未暴露；历史上无任何真实用户走到此路径（L3 脚本是第一个）
- **修复**：BizChangeEvent 三个 setter（affectedAccounts/supportingDocs/affectedWbsIds）单点归一化（Jackson convertValue + TypeReference），治愈校验/审批/Outbox payload 全部读路径；新增回归 BizChangeEventJsonListTest 4 例（模拟 TypeHandler 裸 List 写入形态）
- **次要修复**：ChangeEventStatus.assertTransition 的 IllegalStateException → BusinessException（可预期业务约束不再落「系统内部错误」兜底，符合 AGENTS.md 错误语义约定）；5 处测试断言同步更新
- **验证**：`mvn -pl zw-contract -am test` BUILD SUCCESS，374 tests 0F0E0S
- **test-api-project.sh 清理假阳性**：业务流推进到 TENDERING 后「仅草稿可删」拒绝 DELETE，脚本无条件打印"已清理"→ 每跑一次沉淀一条 `API测试项目` 租户 1 残留（本次 12:50 亦然）。修复：清理后 GET 核验，删除未生效时显式警告残留及清理工具（现状记录不判 FAIL，沿用 tenant-isolation 探针惯例）
- **残留处置**：3 条 ASSESSING 变更事件经真实 API cancel（code=200×3）；tenant-isolation 的 P9 墓碑 2 行（biz_project/biz_construction_contract，deleted=1）按纪律先 mysqldump 备份后物理清除
- **终验审计**（05:37Z）：**PASS=67 FAIL=0 WARN=0 INFO=39 基线完全恢复**（中途 4.4 曾 WARN=1，即上述墓碑）

### 待用户决策（阻塞验收项 2 收尾）——2026-10-05 13:40 问询未获答复，按合同保守处置：均不执行，目标停在可解除的 blocker 状态
1. **部署 P0 修复**：修复在后端代码，需 CI fast_deploy（约 3-5 分钟，重启生产容器、服务短暂中断）后重跑 change-event L3 收全绿。合同约定生产容器重启须用户批准。解除命令：`gh workflow run deploy.yml -f fast_deploy=true`，部署完成后 `ssh` 服务器 `cd /root/zwi-deploy && bash test-api-change-event.sh`（脚本已对齐现行契约并同步服务器）
2. **垃圾数据 --execute**：dry-run 350 行（分支仅命中今天的 1 条 API测试项目；其余为历史任务产物堆积：rolling forecast 198/monthly_analysis 43/risk_register 40 等；守卫全过、累计值回滚正确跳过；存活项目仅剩 4 演示种子）。解除命令：服务器 `bash /root/zwi-deploy/cleanup-garbage-data.sh --execute`

### 最终验收状态（本轮停止点）
- 验收 1/3/4/5/6：✅ 全部有证据（L1 22/22；L4 26/26+清理四项；审计 PASS=67 FAIL=0 WARN=0×2 轮；资金不变量三层证据；报告落盘）
- 验收 2：🟡 27/28 全绿，change-event 24/32 待 P0 部署后复测（可解除 blocker，非技术障碍）
- 无进展轮数未触及；按合同「真 blocker → 停止」条款终止本轮，等待用户对上述两项的决策

## 备注（目标外发现）
- 滚动预测分页返回历史月快照（生成时点值）；消费方若取「第一条」而非「当月」可能读到历史值——产品设计问题，只记录
- batch2/3/4 的 API 级清理留墓碑（deleted=1），verify-l4-clean 语义下干净，仅审计 4.4 裸 COUNT 可见；L4 兜底物理清理可清——不改脚本
- 审计 audit-data.ps1 的 scp 下载步骤被 SSH post-quantum 警告（stderr）+ $ErrorActionPreference='Stop' 中断，报告需手动 scp（两次复现）——工具小瑕疵，只记录
- 单模块 `mvn -pl zw-contract test`（无 -am）会解析 ~/.m2 旧 zw-common jar 出现 UrgeNotifyEvent NoSuchMethod 假失败，须带 -am——环境注意事项

---

# 历史合同：付款审批、成本变更、复发风险优化

## 当前确认合同
- 最终状态：付款审批有源单依据、失败不允许操作、批量范围与金额明确；变更批准额与带符号账户明细一致且账户属于当前项目；已解决风险复发重开且保留处理记录。
- 验收：100/120万与0/非零变更拒绝；合法事件重复消费只入账一次；风险命中→解决→再命中恢复OPEN及活跃汇总；审批正常/失败/空态、退回终止及权限回归；受影响后端与前端测试、类型、构建、接口审计和真实界面验证有证据。
- 不变量：不混审批/现金/成本口径，不削弱权限/幂等/测试，不部署、不写生产、不用mock替代真实验收。
- 边界：仅三条链相关源码、测试、Spec和改造记录；不扩展资金预测/结项/整改/全站重设计，不新增不必要依赖。
- 迭代：基线→最小失败回归→最小修复→受影响验收→更新证据。
- 停止：全部验收有证据才完成；环境受阻立即登记并由用户决定修复/延期/缩减，不能虚报通过。

## 当前假设账本
| 假设 | 依据 | 状态 |
|---|---|---|
| 三条链可复用现有审批详情、Outbox、账本和风险记录，不需新模块 | 前次源码审阅 | 待本轮核对 |

## 当前轮次记录
### R1
- 用户确认执行；保留现有用户改动（测试受阻台账与system API）及已有未跟踪文件，不清理历史产物。开始建立基线。
### R2
- Maven裸命令缺失，既有绝对路径3.9.16恢复；接口基线891/774/88，Critical0/Major0/Minor341。
- 风险baseline13/13；新增复发测试确实失败（OPEN vs RESOLVED）；最小重开修复后Corretto21模块246/246通过。Adoptium attach失败已登记解除；forkCount=1参数未覆盖pom硬编码1C，不宣称fork串行。
- 复核handle_note VARCHAR(500)，补500字原备注不覆盖的回归，复发事实日志记录，不新增迁移。
- 本地源码Vite只读验证服务127.0.0.1:3000，未部署；现有登录态失效，用户选择修复，等待登录确认。
### R3
- 当前源码contract,budget,dashboard -am test退出0，旧common构造签名依赖阻断解除。报告头统计均failures/errors/skipped=0，但dashboard普通测试与property测试引擎统计不同，不能简单将旧246与本次138直接比较。
- 付款补信任边界实际红灯2失败9通过，修后专项50/50；额外旧workflow-pages审批测试同步中，保持API/失败保留/取消守卫覆盖。
- 最新Vite build54.14s成功。完整typecheck exit1（既有测试及财务/材料/项目类型问题），未降级为通过。完整Vitest串行1249pass/4failed/2skip+2unhandled，4审批旧测试本轮修订，路由异常单独探查，不改timeout/skip。
- 最新接口891/775/88，Critical0/Major0/Minor340，无新增重大错位。
### R4
- 当前工作区git diff --check退出0；旧行尾已恢复，未触碰用户system API与历史临时产物。
- 父最终审批相关5个文件73例全部通过；当前构建通过。用户明确延期存量type与路由异常，不扩大范围，不标完整门禁通过。
- 已验证绝对目标并仅删除本轮只读浏览器探针；审计报告保留作为验收证据。Vite本地服务保留，等待用户登录以完成真实只读UI，不自动尝试凭证、不写审批。
- 本轮目标未完成：真实登录态尚无用户完成确认；不重复执行失效登录探针，不把等待视作已验收。
### R5
- 用户已确认登录并要求继续；goal已resume。核对本地3000服务仍监听，但9222无可连接调试端口；用户浏览器与Playwright独立context不共享登录态，不冒称接管。
- 真实UI采取用户协助的只读截图证据：待办列表、付款详情，或真实空态/错误截图；不请求token/密码，不执行审批。等待截图，登录确认本身不能替代任务页验收。
### R6
- 用户报告内容区再次出现蒙层。源码diff确认本轮审批页将原el-skeleton改成了el-table上的v-loading；Element Plus由此插入absolute `.el-loading-mask`（z-index 2000、mask背景），根因明确，不是布局nav-backdrop。
- 已移除审批表格v-loading并恢复el-skeleton加载态，未改业务门禁。新增源码回归：必须存在骨架、el-table禁止v-loading，防复发。
- 首次新回归因Vitest的import.meta.url非file scheme失败，属测试路径错误，不是产品失败；改用process.cwd稳定路径后7/7通过。同期workflow两套旧测试59/59通过，仅该新断言路径错误；最新Vite build退出0。
### R7
- 用户真实截图证明前次定位不完整：截图左侧导航亮、右侧从导航边界起整屏变暗，确系DefaultLayout移动导航`.nav-backdrop`，不是审批loading。1920物理像素在内置浏览器约2x缩放下CSS viewport约960，误中max-width:992px。
- 将脚本matchMedia和CSS抽屉断点同步收紧到767px；此为真实手机/小平板窄屏范围，约960px桌面内置视口保持桌面导航、不生成遮罩。新增源码守卫禁止布局残留992px断点。
- 两类蒙层专项15/15通过，Vite build 44.34s退出0。等待用户刷新后真实截图/确认；不再把代码证据冒充界面证据。

---

# 历史合同：重构 PC 端统一导航体系

## 最终状态
ZW-Insight PC 端形成“业务域侧栏 + 固定二级导航面板 + 常用/最近入口 + Ctrl+K 全局搜索”的统一导航；现有 114 个可见页面按任务归入不超过 10 个业务域，被降级或合并的功能仍按原权限和旧 URL 安全可达。

## 验收
- 一级业务域 ≤ 10；普通业务角色通常可见 ≤ 7。
- 全部现有可见路由有明确处置：主导航、聚合入口、上下文入口、管理员专属或暂时隐藏。
- 侧栏、二级面板、常用、最近访问、Ctrl+K 共用同一授权叶子集合。
- DIR 不扩权；hidden/BUTTON/禁用/未知路由不进入导航；菜单 API pending/error 均 fail closed 并可重试。
- 桌面端固定二级面板；移动端抽屉；键盘、焦点和 aria 状态可用。
- 原路由、书签、API、权限码、隐藏详情/表单路由保持兼容。
- navigation、布局、command palette、router guard 测试通过；typecheck、build、stylelint、一致性审计不新增 Critical；完成桌面与移动视觉验收。

## 不变量
- 后端权限仍是安全边界；前端隐藏不冒充授权。
- 不物理删除真实业务能力，不混合不同财务数据口径。
- 不以全量静态菜单作为 API 失败 fallback。
- 不削弱、跳过或删除既有测试，不新增导航依赖。
- 不修改后端 Controller、数据库或生产数据；系统监控仅隐藏入口。

## 边界
- 允许修改 `zw-insight-web/src/{layouts,components,composables,stores,router,api,utils,__tests__}` 与本功能 Kiro Spec。
- 后端、数据库、具体业务页面原则上只读；必须越界时先停下说明。

## 迭代策略
每轮执行：证据 baseline → 更新假设 → 最小可归因改动 → 完整验收 → 记录结果。不得重复已证伪路线。

## 停止条件
全部验收项有证据通过方可完成；连续 3 轮无实质进展或真实环境阻塞则停止，记录尝试、证据、原因、影响与解除条件。

## 执行指示
每轮结束更新下方“假设账本”和“轮次记录”；只按本合同停止条件终止。

## 假设账本
| # | 假设 | 支持证据 | 反对证据 | 状态 |
|---|---|---|---|---|
| 1 | 静态路由可作为页面目录，后端用户菜单作为唯一可见授权来源 | 当前布局已采用两者交集 | DIR fallback 会扩权 | 当前最强 |
| 2 | 不改业务页面即可先通过导航分组显著降低认知负担 | 114 个叶子可映射到 9 个业务域 | 聚合 Tab 尚非本轮实体合并 | 当前最强 |
| 3 | localStorage 足够承载第一版常用与最近访问 | 无需新增后端 API | 暂不跨设备同步 | 当前最强 |

## 轮次记录
### R1
- 假设：菜单膨胀主要由平铺结构、重复入口与低频配置导致 → 改动：完成 114 个菜单代码审计与 IA 合同 → 结果：确认 9 个目标业务域、合并/降级/隐藏边界 → 下一实验：建立统一导航模型与测试。

### R2
- 假设：静态路由与明确 MENU 授权取交集可消除 DIR 扩权且不改后端 → 改动：新增纯导航模型、8 个业务域映射、业务域 rail、固定二级面板、常用/最近、移动抽屉，并让 Ctrl+K 同源 → 结果：30 个导航相关测试通过、Vite production build 与 stylelint 通过；Impeccable detector 发现并移除 width animation → 下一实验：补布局测试、类型隔离复核与视觉验证。

### R3
- 假设：仓库既有真实登录状态可用于本地源码视觉验收 → 改动：启动 Vite 并连接真实远程 API，尝试真实验证码 setup 与既有 storageState → 结果：旧验证码 setup 已不适配现有 SliderCaptcha；既有 JWT 已过期并返回 401，无法进入授权导航；未使用 mock 降级 → 下一实验：更新真实 E2E 登录夹具以支持 SliderCaptcha，或取得有效真实登录态后完成三视口验收。

### R4
- 假设：按真实 `gapPct` 拖动 SliderCaptcha 可恢复无 mock 的 E2E 登录 → 改动：更新 auth-real setup，使用页面真实 slider challenge、真实 verify 和真实登录；启动本地源码连接远程 API → 结果：真实登录 1/1 通过；1440/768/375 三视口均呈现 8 个业务域、1 个活动域、固定/抽屉面板和 9 个当前域链接，横向 overflow=false，移动 drawerOpen=true，无菜单错误态 → 下一实验：复跑最终回归并完成目标。
