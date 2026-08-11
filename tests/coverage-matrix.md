# 功能-测试追溯矩阵（coverage-matrix）

> 生成日期：2026-08-10（阶段 0 盘点产出，git 追踪，每批补全后销项更新）
> 基准：docs/工程项目管理系统功能表.md（46 个功能模块）× 代码实测（126 Controller / 150 Service / 225 测试类 / L3 8 脚本 / L4 19 阶段 / L5 56 spec）
> 补全定义见 .kiro/specs 批准的 L1-L5 测试补全计划；本文件只回答"哪里缺、缺多少、先补谁"

## 状态标记

- OK：该层已有真实测试映射
- PARTIAL：部分覆盖（有测试但端点/分支不全）
- GAP：无覆盖，需补
- EXEMPT：登记豁免（需附理由）

---

## 一、L1 单元测试缺口（Service 层无专属单测，按模块）

判定标准：Service 存在但无对应 *ServiceTest/*Test 单测类（Property 测试单独存在不算单元覆盖）。

| 模块 | 缺口 Service（数量） | 已有覆盖备注 |
|---|---|---|
| zw-file | ~~BatchImportExportService, ExportScheduleService, FilePreviewService, PdfConvertService, PrintTemplateService, SerialNumberService, StorageService, TemplateService（8）~~ **已补全（批 5，8 类 37 例；wkhtmltopdf/MinIO 真实链路由生产验证）** | FileUpload/Minio/ThymeleafRender 已覆盖 |
| zw-hr | ~~RegularApplyService, TransferApplyService, SealApplyService, VehicleService, VehicleApplyService, VehicleMaintenanceService, HrStatisticsService（7）~~ **已补全（批 3，7 类 44 例）** | Entry/Resign/OfficeSupply 已覆盖 |
| zw-system | ~~AuditLogService, BackupService, SysLogService, SysTenantTypeService, VersionManagerService, SystemConfigService（6）~~ **已补全（批 4，6 类 33 例；Backup 真实 mysqldump 链路由生产验证，单测覆盖守卫/失败落库分支）** | Dict/Menu/Org/Post/Role/Tenant/User 已覆盖；DataPermission 有集成测试 |
| zw-site | ~~CompletionAcceptanceService, RectificationService, SchedulePlanService, ScheduleFeedbackService, LocationSignService, ReminderLogService（6）~~ **已补全（批 3，6 类 50 例）** | Inspection/ConstructionLog/ReminderDedup/ReminderTask(集成) 已覆盖 |
| zw-tender | ~~CompanyCertificateService, DepositReturnService, PersonCertificateService, TenderFeeService, TenderTaskService（5）~~ **已补全（批 2，5 类 32 例，commit fa7d3fd）** | TenderRegister/DepositApply/OpenBidRecord 已覆盖 |
| zw-message | ~~NoticeService, PushConfigService, TemplateService, UserShortcutService, WeChatWorkService（5）~~ **已补全（批 4，5 类 27 例；WeChatWork 真实外呼分支 mockStatic 拦截 HttpUtil 覆盖）** | Announcement/Message 已覆盖 |
| zw-finance | ~~InvoiceReceivedService, OtherPaymentService, ReserveFundReturnService, TaxRateService（4）~~ **已补全（批 1，4 类 28 例）** | 其余 13 个已覆盖（含 Lock 19 例） |
| zw-security | ~~AuthService, DeviceManagerService, LoginLocationService, AliyunSmsService（4）~~ **已补全（批 1，4 类 24 例；AuthService 密码主路径原由 TokenServiceTest 覆盖，补 SMS/租户分支）** | Captcha/PasswordReset/Token(Auth 相关)/Permission/DocsBlock 已覆盖 |
| zw-workflow | ~~ProcessDefinitionService, UrgeConfigService, UrgeService（3）~~ **已补全（批 2，4 类 26 例含 ApprovalRollbackServiceImpl，commit fa7d3fd）** | Approval/BusinessType/Delegate/Rollback 已覆盖 |
| zw-machine | ~~MachineOilRecordService, MachineUsageRecordService, MachineWorkLogService（3）~~ **已补全（批 3，3 类 21 例）** | Contract/Entry/Ledger/Repair/WorkSettlement 已覆盖 |
| zw-basedata | ~~CompanyService, InspectionSchemeService, OwnerService（3）~~ **已补全（批 4，3 类 15 例）** | Material/Supplier/Blacklist/Evaluation/Category 已覆盖 |
| zw-dashboard | ProjectDashboardService（1） | DashboardService 已覆盖；ProjectDashboard 仅属性测试 |
| zw-app | SupplierAutoScoreService（1） | 其余为 Controller 测试 |
| zw-archive | ArchiveService（1） | 仅 ArchiveSearchFilterPropertyTest |
| zw-common | 覆盖率 24.7%：异常体系/基类/R 包装等零散缺口 | AssertUtils/Desensitize 已覆盖，逐项评估豁免 |

**L1 合计缺口：57 个 Service**（批 1 已销项 8，批 2 已销项 9，剩 40；豁免评估后实际补测量预计 32~40）。
已全量覆盖模块：zw-budget、zw-purchase、zw-labor、zw-material、zw-subcontract、zw-contract、zw-project。

## 二、L2 集成测试缺口

现有 12 类（zw-app integration 包）+ 模块直连集成测试（contract/finance/project/system×3/site）。
批 2 新增 BudgetBlockIntegrationTest（zw-budget）+ TenderFlowIntegrationTest（zw-tender），本地实跑 14/14 全绿、零残留。

| 缺口 | 需验证的核心行为 | 优先级 |
|---|---|---|
| ~~zw-budget 管控 BLOCK~~ ✅ 批 2 完成 | 超预算禁止提交 vs 仅提醒两种强度的真实 DB 场景 | 高（金额） |
| ~~zw-tender 投标流转~~ ✅ 批 2 完成（保证金 submit 依赖流程部署由 L4 覆盖） | 报名→开标中标→项目状态联动→保证金支付/退回 | 高（金额） |
| ~~zw-security 登录链~~ ✅ 批 4 完成（本地实跑 6 例全绿；dev 验证码开关关闭时自适应 skip 并如实记录） | 高（权限） |
| zw-workflow 审批链 | Flowable 已有 FlowableIntegrationTest；缺业务单据挂流程端到端（已有 ApprovalRollback 覆盖回滚） | 中 |
| ~~zw-hr 审批流~~ ✅ 批 4 改由 L4 9H 覆盖（入职审批→自动建账号，依赖 entry_apply_approval BPMN，待 CI 实跑） | 中 |
| zw-message 通知 | 业务事件→消息落库→已读状态 | 中 |
| zw-archive 聚合 | 档案视图跨表聚合正确性 | 低（只读） |
| zw-dashboard 聚合 | 看板统计 SQL 正确性 | 低（只读） |

## 三、L3 接口契约脚本缺口（最大缺口层）

现有 8 个 shell 脚本仅覆盖 8 个业务模块的部分端点。126 Controller 中约 **70% 无任何 L3 脚本覆盖**。

| 已覆盖（PARTIAL/OK） | 未覆盖模块（需新脚本） |
|---|---|
| project（主端点）/ contract / subcontract / purchase（contract+inquiry）/ labor（contract+team+roster+work-order）/ machine（contract+ledger+部分）/ material（inbound+outbound+stock+inventory）/ finance（原 3 组 + **批 1 新增 test-api-finance2.sh：开票/收票/汇总/其他付款/报销/结算/备用金/质保金/税率 CRUD/财务锁**）/ **system+auth（批 1 新增 test-api-system.sh：用户/机构/角色/菜单/岗位/字典 CRUD/运维端点/租户/设备/验证码）** / **tender（批 2 新增 test-api-tender.sh：6 组分页契约 + 登记/费用/人员证书/企业证书 4 条 CRUD 零残留闭环）** / **budget（批 2 新增 test-api-budget.sh：编制/变更/管控配置/控制配置只读契约 + @Valid 负向）** / **workflow（批 2 新增 test-api-workflow.sh：待办/已办/流程定义/业务类型/催办/回滚/委托契约 + 非法流转负向）** / **hr（批 3 新增 test-api-hr.sh：10 组分页契约 + statistics/overview + 7 条 submit 负向 + 入职/车辆/办公用品 3 条 CRUD 零残留闭环）** / **site（批 3 新增 test-api-site.sh：6 组分页契约 + 施工日志/进度计划 2 条 CRUD 零残留闭环 + 签到/催办只读契约 + 8 条不存在负向）** / **basedata（批 4 新增 test-api-basedata.sh：8 组分页契约 + 公司/甲方/检查方案/材料/供应商 5 条 CRUD 闭环 + 评价隔离闭环 + 黑名单只读）** / **message（批 4 新增 test-api-message.sh：4 组分页契约 + 公告全生命周期闭环 + 模板/推送配置 CRUD 闭环 + 站内信/快捷入口只读契约）** / **dashboard（批 4 新增 test-api-dashboard.sh：公司级+项目级看板 18 端点只读契约 + 404 负向）** / **archive（批 4 新增 test-api-archive.sh：4 类实体档案 + 3 组分页档案只读契约）** | supplier-portal 公开接口 |

补充说明：`zw-insight-web/e2e/api-tests/`（vitest，20 spec 347 用例）已按功能表注释覆盖全部 20 个模块域的 API 功能，但**未接入 CI**——shell 脚本层仍是 CI 内唯一 L3 门禁，缺口照算；api-tests 接入 CI 列入阶段 5 一并评估。

**批 1 L3 CI 实跑验证（run 31399388581）**：test-api-finance2.sh 37/37 通过、test-api-system.sh 43/43 通过，L3 共 10 脚本 通过=10 失败=0。首轮 run 31395791226 的 12 处失败经逐条对照 Controller 定位均为脚本端点写错（非后端缺陷），已按后端为准修正（db2face）。

**批 2 L3 CI 实跑验证（run 31403596908，首跑一次通过）**：test-api-tender.sh 50/50、test-api-budget.sh 26/26、test-api-workflow.sh 24/24，L3 共 13 脚本 通过=13 失败=0；同 run Backend Build 实跑含批 2 新增 58 例单测全绿。脚本均先精读对应 17 个 Controller 再编写（吸收批 1 教训）。

**批 3 L3 CI 首轮（run 31439179357，L3 通过=13 失败=2）→ 修复后复跑 run 31441261373 全绿（L3 通过=15 失败=0）**：
- test-api-hr.sh 1 处断言写错：「出入库-不存在用品被拒绝」——「关联用品不存在」校验实际在 submit 而非 save，且创建出入库记录无 DELETE 端点会残留，已删该断言（分支由 L1 覆盖）
- 后端真缺陷 ×2：`biz_reminder_config`/`biz_reminder_log` 缺 BaseEntity 要求的 `created_by/deleted/version`（log 另缺 `updated_at`），导致 `GET /api/v1/site/reminder-logs/{id}` 与 `GET /api/v1/site/reminder-config` HTTP 500。修复：Flyway `V2026_38` + db-init `40_V2026_38` 幂等补列，db-init `12_V2026_09` 建表同步补齐
- 连带修复 CI 编排：Init L4 tenant 步骤默认 `if: success()` 被 L3 失败拖住跳过（t9999admin 登录失败 L4 未跑），已加 `if: always()`
- L4 批 3 分支（9F 变更签证回写合同累计变更金额 / 9G 质保金登记-到期-返还闭环）已写入 lifecycle-sim-v2.sh + 2 个新 BPMN，复跑同 run 首跑全绿（成功=59 失败=0）

## 四、L4 生命周期阶段缺口

现有 19 阶段：1 报备 / 2 立项 / 3 投标登记 / 3B 开标中标 / 4 施工合同 / 5 预算编制 / 6 支出合同 / 7 现场管理 / 7B 材料入出库 / 7C 机械 / 7D 劳务 / 7E 分包 / 8 产值结算 / 9 财务收付 / 9B 竣工结算 / 9C 采购结算 / 9D 付款闭环 / 9E 驳回分支 / 10 项目关闭。

对照功能表的缺失分支：

| 缺口分支 | 功能表依据 | 优先级 |
|---|---|---|
| ~~变更签证（登记→审批→合同累计变更回写）~~ ✅ 批 3 完成（阶段 9F，CI run 31441261373 绿） | 5.2 | 高 |
| ~~质保金登记→跟踪→退还申请~~ ✅ 批 3 完成（阶段 9G，CI run 31441261373 绿） | 5.1 | 高（金额） |
| 材料退货出库（含退货退款分支） | 2.5 | 中 |
| 备用金申请→报销冲抵→归还 | 2.9 | 中（金额） |
| 保证金退回登记（投标侧） | 1.4 | 中（金额） |
| 合同到期提醒触发验证 | 5.11 | 低 |
| ~~HR 流程（入职审批→档案）抽样~~ ✅ 批 4 提前完成（阶段 9H，CI run 31451226266 绿，账号自动创建断言通过） | 2.10 | 低 |

## 五、L5 前端 E2E 现状与缺口

| 资产 | 规模 | 状态 |
|---|---|---|
| e2e/api-tests（vitest API 功能测试） | 20 spec / 347 用例 | 未接入 CI，运行状态未验证 |
| e2e/tests（Playwright UI，mock 模式） | 13 spec / 89 用例 | 未接入 CI，运行状态未验证 |
| e2e/tests/real（真实模式打服务器） | 3 spec / 29 用例（**12 skip**：login 4 / project-crud 2 / workflow 6） | 未接入 CI |
| e2e/consistency（一致性审计） | 20 spec / 54 用例（2 skip） | 未接入 CI |

缺口：①全部未接入 CI（门禁为零）②real 模式仅 3 spec 且 41% 用例被 skip ③UI 覆盖缺投标/现场/HR/档案页面 ④skip 原因未盘点（阶段 5 逐个实跑定性）。

---

## 六、补测优先级（风险导向：金额/审批/权限/状态机 > 高频业务 > 基础数据）

| 批次 | 内容 | 理由 |
|---|---|---|
| 批 1 | L1：zw-finance 4 缺口 + zw-security 4 缺口；L3：finance 补齐 + system/auth 新脚本 | 金额与权限核心 |
| 批 2 | L1：zw-tender 5 + zw-workflow 3；L3：tender + budget + workflow 新脚本；L2：budget BLOCK + tender 流转 | 投标/审批链金额敏感 |
| 批 3 | L1：zw-hr 7 + zw-site 6 + zw-machine 3；L3：hr + site 新脚本；L4：变更签证 + 质保金分支 | 高频业务 + 金额分支 |
| 批 4 | L1：zw-system 6 + zw-basedata 3 + zw-message 5；L3：basedata + message + dashboard/archive 新脚本；L2：security 登录链 + hr 审批 | 基础数据与通知 |
| 批 5 | L1：zw-file 8 + zw-dashboard 1 + zw-archive 1 + zw-app 1 + zw-common 评估；L3：file 新脚本；L4：退货/备用金/保证金退回/到期提醒分支 | 支撑功能收尾 |
| 批 6 | L5 全量：56 spec 实跑盘点→修复 skip→补核心页面→CI 接入（api-tests 一并评估接入） | 独立阶段 |

每批验收：模块 mvn test 全绿 → CI 全链路绿 → 本矩阵销项 → tasks.md 登记 → coverage-baseline.json 更新。

## 七、豁免登记区（补全过程中填写）

| 对象 | 层级 | 豁免理由 | 日期 |
|---|---|---|---|
| AliyunSmsService 真实外呼分支 | L1 | 依赖阿里云 SMS 通道，开关分支与凭证缺失显式失败分支已测（AliyunSmsServiceTest 3 例）；真实发送链路待生产环境验证 | 2026-08-10 |
| WeChatWorkService 推送分支 | L1 | 企业微信外部依赖，同上（候选，批 4 确认） | 待定 |
| zw-common 框架基类 | L1 | 纯框架代码逐项评估（候选） | 待定 |
