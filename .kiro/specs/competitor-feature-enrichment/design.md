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

## 经营卡与修订评估

- views/cockpit/project-operation.vue 已含合同收入、预计成本、预计利润、利润率、回款率及利润趋势/回款付款月度卡（约27-188行），并于84行说明成本口径退化；本次不另造经营卡，不重复已有接口。
- ConstructionContractService.update 约143行严格仅草稿可编辑；onApproved 约177行生效并回写项目。当前无独立 revision 生效事件/版本快照/差额反冲协议，直接开放已生效改价会破坏下游快照与额度。按批准范围仅评估，不实施改价。

## 采购安全受限的具体证据与处理

- BizMaterialOutboundDetail 只有 outboundId/boqItemId/materialName/specification/unit/quantity/unitPrice，无 inboundId/inboundDetailId。BizMaterialOutbound 只有 contractId、returnType 等，无来源入库单。不能用材料名称或BOQ相同推断退货来源批次。
- MaterialInboundReadMapper 仅返回头表总额；PurchaseSettlementService 的 create/update 仅校验入库总额，settlementExistsForInbound 为整单排他，无行级已结/在途占用账。存量 APPROVED 结算无数量，不能按金额反推已结数量。
- 因此不能安全交付可用的行级分批结算。本次实体显式接收 details 并在 create/update 写入前返回409，阻止 JSON 明细被忽略后意外整单入账；原整单路径、审批回写及旧单保留。不创建永远无法可靠结算的子表，也不虚报占用/释放/级联已完成。
- 解锁条件：业务确认并补录真实退货来源入库行、建立存量结算行数量的人工核对映射，再实现行锁与数量账的占用/审批转换/驳回释放/级联。未取得真实映射前不得启用。

## 验证与回滚

前端运行新增回归与既有页面测试；后端与 MySQL 迁移尚未运行，依用户禁令登记，不宣称通过。回滚应用代码时可保留新增可空列；索引/生成列如需移除须经数据库备份与独立审批。本期无环境专属行为与预设数据修改。
