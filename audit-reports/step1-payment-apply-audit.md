# 付款申请模块数据一致性审计报告（第 1 轮）

**审计日期**: 2026-08-17  
**审计范围**: biz_payment_apply 表字段漂移 / 静默写失败 / 金额勾稽断裂  
**数据来源**: SQL 探针 + Controller/API 对比 + E2E 测试载荷  

---

## 1. 字段映射偏差

### 1.1 MySQL Schema vs Java Entity 对照矩阵

| API 字段 | DB 列 | Java 实体 | Nullable | 类型匹配 | 状态 | 备注 |
|---------|-------|----------|----------|---------|------|------|
| id | id | Long ✅ | NO | BIGINT(20) | ✅ Active | 雪花 ID |
| projectId | project_id | Long ✅ | NO | BIGINT(20) | ✅ Active | 必填 |
| projectName | (虚拟列) | String ✅ | YES | N/A | ⚠️ 不持久化 | @TableField(exist=false) 设计正确 |
| contractId | contract_id | Long ✅ | NO | BIGINT(20) | ✅ Active | 必填 |
| contractCategory | contract_category | String ✅ | YES | VARCHAR(50) | ✅ Active | 合同分类枚举 |
| supplierId | supplier_id | Long ✅ | YES | BIGINT(20) | ✅ Active | 供应商关联 |
| supplierName | supplier_name | String ✅ | YES | VARCHAR(100) | ✅ Active | 冗余展示字段 |
| paymentAmount | payment_amount | BigDecimal ✅ | NO | DECIMAL(18,2) | ✅ Active | **核心金额字段** |
| paymentDate | payment_date | LocalDate ✅ | YES | DATE | ✅ Active | 付款日期 |
| cumulativeSettlementSnapshot | cumulative_settlement_snapshot | BigDecimal ✅ | YES | DECIMAL(18,2) | ❌ **Zombie Field** | 从未读写 → 建议删除 |
| unpaidAmountSnapshot | unpaid_amount_snapshot | BigDecimal ✅ | YES | DECIMAL(18,2) | ❌ **Zombie Field** | 从未读写 → 建议删除 |
| status | status | String ✅ | YES | VARCHAR(20) | ✅ Active | DRAFT/APPROVED |
| workflowInstanceId | workflow_instance_id | String ✅ | YES | VARCHAR(50) | ✅ Active | Flowable 实例 ID |
| tenant_id (inherited) | tenant_id | Long ✅ | YES | BIGINT(20) | ✅ Active | 租户隔离 |
| created_at (inherited) | created_at | LocalDateTime ✅ | YES | DATETIME | ✅ Active | 创建时间 |
| updated_at (inherited) | updated_at | LocalDateTime ✅ | YES | DATETIME | ✅ Active | 更新时间 |
| deleted (inherited) | deleted | Integer ✅ | YES | INT | ✅ Active | 逻辑删除标记 |
| version (inherited) | version | Integer ✅ | YES | INT | ✅ Active | 乐观锁版本 |

### 1.2 关键发现

#### 🔴 P0 缺陷：僵尸字段存在误导风险
- **cumulative_settlement_snapshot** / **unpaid_amount_snapshot** 
- **证据**: Service 层从未调用这两个字段的 setter/getter（grep 代码库确认）
- **影响**: 开发人员可能误以为系统支持“可付金额快照”，实际运行时计算实时值
- **修复建议**: 删除字段或补充 Javadoc 明确其废弃计划（参考 Plan C 分析）

#### 🟡 P1 潜在问题：payment_date 为 Optional
- 数据库允许 NULL，但业务语义上应该是必填
- **证据**: Step 1.3 中 APPROVED 记录都有值，但 DRAFT 阶段可为空
- **影响**: 前端筛选器若期望 time range 查询，后端无对应字段支持

---

## 2. 数据质量异常

### 2.1 Tenant 9999 统计快照
```sql
DRAFT   250
APPROVED        4
```

**观察**:
- DRAFT:APPROVED = 62.5:1，说明大部分付款申请停留在草稿态（需进一步排查审批流程阻塞点）
- 仅 4 笔已生效付款，金额从 5,000~20,000 不等

### 2.2 金额勾稽断裂探测
执行回写验证查询（与 biz_other_contract 关联）：
```sql
SELECT 
    pa.contract_id, SUM(pa.payment_amount) AS total_paid_from_apply,
    oc.cumulative_paid AS contract_record,
    CASE WHEN ABS(SUM(pa.payment_amount) - oc.cumulative_paid) < 0.01 THEN 'MATCH' ELSE 'MISMATCH' END AS reconciliation_status
FROM biz_payment_apply pa
JOIN biz_other_contract oc ON pa.contract_id = oc.id AND oc.deleted = 0
WHERE pa.tenant_id = 9999 AND pa.status = 'APPROVED'
GROUP BY pa.contract_id;
```

**预期结果**: 全部 MATCH（每笔 APPROVED 付款都应在对应合同表产生累计回写）  
**实际执行**: *等待动态测试步骤 3 的结果*  

---

## 3. 并发安全性验证

当前 SUBMITTED 状态数量为 **0**（Step 1.2 结果），暂无法验证竞态条件防护。需在 Step 3 构造双重提交测试用例。

---

## 4. 结论与待办

### 4.1 本模块审计结论
✅ **字段一致性良好**：API 载荷与 DB 物理列完全对齐（除 2 个僵尸字段外无遗漏/冗余）  
⚠️ **设计冗余**：zombie fields 存在误导性文档风险  
🔴 **待验证**：金额回写链路是否正确、并发防护是否生效  

### 4.2 建议修复优先级
1. **P0**: 清理/cumulative_settlement_snapshot/unpaid_amount_snapshot 字段（或删除或补注释）
2. **P1**: payment_date 添加 NOT NULL 约束（如业务要求必填）
3. **P2**: 补充并发竞态条件单元测试 + E2E 测试

### 4.3 下一步行动
- 继续执行 Step 3（动态测试验证回写链路）
- 并行启动 Contract/Project 模块字段映射提取
- 生成最终汇总报告（含六轮进度概览）

---

**附录 A**: SQL 探针完整日志  
**附录 B**: Controller/Entity 源码引用行号  
  - PaymentApplyController.java: L39 `@RequestBody BizPaymentApply`  
  - BizPaymentApply.java: L21-49 字段定义  
  - schema.sql: L1989-2010 原始表结构设计  
