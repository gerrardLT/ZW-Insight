# 设计与变更记录

## 独立评审 P1 修复（2026-10-15）

- 8位传统号码必须附合法10/12位代码；20位数电号码可不附代码，合法附码被规范为空，SQL guard 使用 D:号码，避免代码有无绕过唯一身份。历史无号码仍兼容。V2026_103 尚未提交/部署，直接同步修正两轨，不修改已应用迁移；若未来发现该版本已应用，须另增迁移而非改 checksum。
- 不把预览强制施加给全模块旧调用方：原 /batch/import 保持无token兼容，携带token仍严格验证；新增 /batch/import/confirm 必填token。通用PC预览对话框走confirm，旧API消费者不迁移也可用。不限定为发票：现有ModuleCode没有发票导入处理器，不能凭空新建发票批处理。
- 保留 should_import_data 无token兼容测试，新增确认有token、缺失拒绝、失效传播与Service过期凭据不创建业务Listener测试。前端契约覆盖旧入口与确认入口分流。

## 已实现增量

- 收票实体增加 invoiceCode/invoiceNumber/amountWithoutTax/taxAmount。Service 规范化数字票号，校验代码与税率；含税金额除以 1+税率，HALF_UP 两位，税额按差额计算，未知税率保留 null。
- V2026_103 双轨迁移：information_schema + PREPARE/EXECUTE；active_invoice_identity 由 deleted=0 的代码与号码生成，租户内唯一。删除记录生成 NULL，可无限删除重建。存量缺号码不回填；异常重复数据使加索引失败而非静默删除。
- DuplicateKeyException 转换为 BusinessException(409)，事务回滚阻止累计收票回写。
- 销项税额与不含税金额通过 getter 派生，无冗余持久化字段。
- 合同表仅用原有累计字段，未收定义为合同额减累计回款，不冒充应收台账余额；汇总仅当前页。
- 我发起流程复用原 Controller GET /api/v1/workflow/approval/my-initiated，映射 processName/startTime，不展示任务办理按钮。

## 已核实复用

ContractStatisticsService.rate 已在分母为零/null 时返回 null，本期不重复修改。

## 第二批增量

- V2026_104 双轨增加签证 visa_date/tax_rate、既有产值明细 part。签证新建日期必填，Service 校验税率0..100，税额按含税变更额派生，负变更保留负税额，历史日期不猜补。
- BOQ part 前端输入、类型、请求和明细持久化贯通，Service 上限200字。
- 盖章件复用 file_info，通过 ContractController /{id}/sealed-files 宿主查询合同及 contract:contract:edit 上传权限；公共 upload/bind 禁止伪造 CONTRACT_SEALED。标签只由实际附件集合派生，加载失败显示未知。
- 合同抽屉三率零分母为 null，隐藏不适用进度条并明示；我发起详情按 processInstanceId 读取既有 trace，绝不当 taskId。
- Excel 新增只读 EasyExcel 解析预览（最多1000行/10MB），不创建业务 Listener。Redis 15分钟一次性凭据绑定租户、用户、模块、项目、额外参数与文件SHA-256，确认原文件才消费凭据并复用既有导入。失败须重新预览；结果按成功/失败行核对，预览不冒充业务校验。

## 项目经营摘要与独立合同修订（本轮追加实现）

- 项目详情挂载 ProjectBusinessSummary，复用 getProjectOverview/getProjectCostControl，展示施工合同额、开票、回款、上报产值及 CBS 当前预算、承诺、实际、预测完工成本。无成本账户明示不可用，缺失金额显示“—”，分区错误与重试独立；异步 generation 防止切项目后旧响应覆盖。不另算现金余额或预计利润，明确 CBS 实际成本不等于审批支出或银行支付。
- ContractRevisionService 独立创建/提交/审批/驳回/草稿作废；记录 baseVersion、before/afterSnapshot、原因与 deltaAmount，锁合同及修订单，接 CONTRACT_REVISION / contract_revision_approval。创建、提交及生效校验基准版本与金额；修订额加签证累计不得低于已发生产值、开票、回款。
- 生效以 EFFECTIVE + 基准 version + 原金额 CAS 更新合同，重算税额、递增业务版本，项目合同额仅原子加差额，重复 APPROVED 回调不重复回写，任一行数失败整体回滚。原生效合同直接编辑限制不放开，修订不记签证累计；项目删除监听器覆盖修订表。
- V2026_106 双轨新增修订表；已落地 Service/API契约/组件测试代码，后端、SQL与真实审批链未运行，不能据静态代码宣称运行通过。

## 采购来源与分批结算（取代前轮 details 全面拒绝）

- V2026_105 双轨增加退货行 inbound_detail_id、结算 settlement_mode 与明细表。退货显式选择来源入库行，后端校验归属与数量，多个来源按稳定顺序加锁；禁止用名称、BOQ或金额猜历史来源。
- PurchaseSettlementService 支持 LINES details，来源行单价由后端读取，数量校验精度/正数/重复/入库归属，金额由数量乘单价派生。来源入库头/行锁与退货共用；余量扣 SUBMITTED/APPROVED 结算及来源退货，提交/通过复核，驳回/撤回/终止状态退出占用集合，删除与级联清理明细。
- 存量及无明细请求保留 LEGACY 整单路径，同源 LEGACY/LINES 禁止混用，旧单不能改分批。合同存在无来源历史退货时仅阻止不可靠的分批计量并要求人工核实，不为其反推数量。409 仅用于明确业务冲突，不再全面拒绝分批功能。
- 已落地 API 来源行接口、前端选择校验、Service与API脚本测试代码；MySQL 8 迁移连跑、真实退货/结算竞态、审批释放及事务回滚均未运行验证。回退前先核对 LINES 新单，不可让旧代码把行级单据当整单重算；保留新增列/表与历史，必要修复经备份和独立数据方案处理。

## D1–D4 财务加固（2026-10-15）

- 开票/签证新增 approvePending CAS，允许 SUBMITTED/REJECTED，重复 APPROVED 与陈旧 DRAFT 在写入前返回；驳回 CAS 仅允许 SUBMITTED。开票合同 SQL 同时校验 EFFECTIVE、正金额与累计开票不超过累计产值；签证用 addCumulativeChangeAmount 原子累加，均检查影响行数。
- 开票占用状态后额度失败仍在同一事务置 REJECTED 并通知，保留修正重审；源合同缺失抛 BusinessException。签证状态 CAS 后累计回写失败抛异常，Spring @Transactional(rollbackFor=Exception.class) 使状态与累计一并回滚。单元测试验证调用顺序与异常；真实事务/并发回滚须隔离 MySQL 验证，不以 Mockito 代替。
- 回款锁源记录 SELECT FOR UPDATE，创建/改额/删除通过 addTotalIncome、addCumulativeReceivedAmount 增减（COALESCE、非负守卫、行数=1），FIFO 核销反冲同事务；改额白名单不开放项目/合同/状态改绑，项目合同归属在信任边界验证。认领与核销亦使用同一源锁，避免覆盖并发改额。
- 付款通过/驳回锁源记录，源合同缺失抛异常；各分类累计付款及项目支出回写检查影响行数，保持审批支出与现金支付字段独立。不新增迁移。
- 回滚采用代码回退，不改存量业务数据；若已发生业务写入，不删除流水或手改累计，按原单据反冲路径处理。

## 验证与回滚

前端运行新增回归与既有页面测试；后端与 MySQL 迁移尚未运行，依用户禁令登记，不宣称通过。回滚应用代码时可保留新增可空列；索引/生成列如需移除须经数据库备份与独立审批。本期无环境专属行为与预设数据修改。
