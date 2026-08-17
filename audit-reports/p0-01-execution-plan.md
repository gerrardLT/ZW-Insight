# P0-01 系统性资金回写断裂 - 三方案并行执行计划

**缺陷等级**: 🔴 P0（系统性数据丢失）  
**影响范围**: 四类支出合同（LABOR/SUBCONTRACT/PURCHASE/MACHINE）+ 项目 total_expense 统计失真  
**根本原因**: Flowable 引擎未初始化运行时表（act_re_procdef/act_ru_task/act_hi_procinst 缺失）→ ApprovalCompleteEvent 未被发布 → PaymentApplyService.onApproved() L192 addCumulativePaid() 未执行

---

## 一、方案 A: 单点修复（优先级最高）

### 1.1 立即行动（24h 内）
**目标**: 恢复付款审批回调链路，手动补偿历史错误数据

#### Step A1: 启用 Flowable 自动建表
```yaml
# application-dev.yml 追加
spring:
  flowable:
    database-schema-update: true  # 自动创建/更新 Flowable 表
    async-executor-activate: false  # 暂不启用异步执行器（避免并发问题）
```

**验证命令**:
```bash
docker restart zwi-backend
docker logs zwi-backend --tail 100 | grep -i "flowable"
docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -e "SHOW TABLES LIKE 'act_%';"
```

#### Step A2: 重新部署流程定义
```sql
-- 确保 payment_apply_approval 流程已正确部署到租户 9999
INSERT INTO wf_process_def (process_key, process_name, version_num, status, tenant_id)
VALUES ('payment_apply_approval', '付款申请审批', 67, 1, 9999)
ON DUPLICATE KEY UPDATE version_num = version_num + 1;
```

#### Step A3: 手动补偿历史数据（4 笔 APPROVED 付款）
```sql
-- 补偿 biz_other_contract.cumulative_paid
UPDATE biz_other_contract oc
SET oc.cumulative_paid = (
    SELECT COALESCE(SUM(p.payment_amount), 0)
    FROM biz_payment_apply p
    WHERE p.contract_id = oc.id 
      AND p.status = 'APPROVED' 
      AND p.tenant_id = oc.tenant_id
)
WHERE oc.tenant_id = 9999
  AND oc.id IN (
    SELECT DISTINCT contract_id FROM biz_payment_apply 
    WHERE tenant_id = 9999 AND status = 'APPROVED'
  );

-- 补偿 biz_project.total_expense
UPDATE biz_project prj
SET prj.total_expense = (
    SELECT COALESCE(SUM(p.payment_amount), 0)
    FROM biz_payment_apply p
    WHERE p.project_id = prj.id 
      AND p.status = 'APPROVED' 
      AND p.tenant_id = prj.tenant_id
)
WHERE prj.tenant_id = 9999;
```

**预期结果**: 
- 4 笔 APPROVED 付款的对应合同 cumulative_paid 从 NULL → 实际金额
- 项目 total_expense 同步更新

---

## 二、方案 B: 全模块加固（中期，1 周内）

### 2.1 幂等守卫增强
为四类合同类型统一增加乐观锁版本号检查：

```java
// PaymentApplyService.java L192 修改前
addCumulativePaid(paymentApply, paymentAmount);

// 修改后（增加并发防护）
@Transactional(rollbackFor = Exception.class)
public void onApproved(Long id) {
    BizPaymentApply paymentApply = paymentApplyMapper.selectById(id);
    if (paymentApply == null || "APPROVED".equals(paymentApply.getStatus())) {
        return;
    }
    
    // 新增：获取当前合同累计已付快照（用于幂等性校验）
    ContractPayableInfo payableBefore = resolvePayable(paymentApply);
    
    // ... 原有逻辑 ...
    
    addCumulativePaid(paymentApply, paymentAmount);
    
    // 新增：回写后二次校验（防止并发重复累加）
    ContractPayableInfo payableAfter = resolvePayable(paymentApply);
    BigDecimal expectedIncrease = paymentAmount;
    if (payableAfter.getCumulativePaid().subtract(payableBefore.getCumulativePaid()).compareTo(expectedIncrease) != 0) {
        log.error("检测到并发重复回写！paymentApplyId={}, expectedIncrease={}, actualIncrease={}",
                id, expectedIncrease, payableAfter.getCumulativePaid().subtract(payableBefore.getCumulativePaid()));
        throw new BusinessException("检测到并发冲突，请重试");
    }
}
```

### 2.2 事件发布链路验证
检查 ApprovalService.complete() 是否正确发布 ApprovalCompleteEvent：

```java
// ApprovalService.java L99-150（需人工审查）
public void complete(String taskId, String comment, Map<String, Object> variables) {
    // ... 任务完成逻辑 ...
    
    // 关键：必须在事务提交前发布事件
    eventPublisher.publishEvent(new ApprovalCompleteEvent(
        businessType, businessId, "APPROVED"));
}
```

### 2.3 集成测试覆盖
新增四类合同类型的端到端测试：

```java
@Test
void testPaymentApply_writesBackLaborContract() {
    // given: 创建 LABOR 合同 + 付款申请
    Long laborContractId = createLaborContract(9999L);
    Long paymentApplyId = createPaymentApply("LABOR", laborContractId, new BigDecimal("10000"));
    
    // when: 提交审批并通过
    paymentApplyService.submit(paymentApplyId);
    approvalService.complete(getTaskId(paymentApplyId), "同意", null);
    
    // then: 验证 LABOR 合同 cumulative_paid 已更新
    BizLaborContract contract = laborContractMapper.selectById(laborContractId);
    assertThat(contract.getCumulativePaid()).isEqualByComparingTo("10000.00");
}
```

---

## 三、方案 C: 数据清洗（必须配合 A/B 执行）

### 3.1 生产环境数据补偿脚本（草稿，需人工评审）
```sql
-- ⚠️ 警告：此脚本将修改生产数据，执行前必须备份！
-- mysqldump --single-transaction --databases zw_insight > backup_$(date +%Y%m%d_%H%M%S).sql

-- Step C1: 备份当前状态（只读）
CREATE TABLE biz_payment_apply_backup_20260817 AS 
SELECT * FROM biz_payment_apply WHERE tenant_id IN (1, 9999);

CREATE TABLE biz_other_contract_backup_20260817 AS 
SELECT * FROM biz_other_contract WHERE tenant_id IN (1, 9999);

CREATE TABLE biz_project_backup_20260817 AS 
SELECT * FROM biz_project WHERE tenant_id IN (1, 9999);

-- Step C2: 补偿 biz_other_contract.cumulative_paid
UPDATE biz_other_contract oc
SET oc.cumulative_paid = (
    SELECT COALESCE(SUM(p.payment_amount), 0)
    FROM biz_payment_apply p
    WHERE p.contract_id = oc.id 
      AND p.status = 'APPROVED' 
      AND p.tenant_id = oc.tenant_id
)
WHERE oc.tenant_id IN (1, 9999);

-- Step C3: 补偿 biz_project.total_expense
UPDATE biz_project prj
SET prj.total_expense = (
    SELECT COALESCE(SUM(p.payment_amount), 0)
    FROM biz_payment_apply p
    WHERE p.project_id = prj.id 
      AND p.status = 'APPROVED' 
      AND p.tenant_id = prj.tenant_id
)
WHERE prj.tenant_id IN (1, 9999);

-- Step C4: 验证结果
SELECT 'payment_apply_total' AS source, SUM(payment_amount) AS total_amount 
FROM biz_payment_apply WHERE tenant_id = 9999 AND status = 'APPROVED'
UNION ALL
SELECT 'other_contract_cumulative_paid', SUM(cumulative_paid) 
FROM biz_other_contract WHERE tenant_id = 9999
UNION ALL
SELECT 'project_total_expense', SUM(total_expense) 
FROM biz_project WHERE tenant_id = 9999;
```

### 3.2 回滚方案
```sql
-- 如果补偿错误，从备份表恢复
DROP TABLE biz_payment_apply;
RENAME TABLE biz_payment_apply_backup_20260817 TO biz_payment_apply;

-- 其他表同理
```

---

## 四、执行时间表

| 阶段 | 任务 | 负责人 | 预计耗时 | 交付物 |
|------|------|--------|---------|--------|
| **Day 1** | 方案 A: Flowable 配置修复 + 手动补偿 | 后端工程师 | 4h | application-dev.yml 更新 + SQL 执行日志 |
| **Day 2** | 方案 A: 验证回写链路 + 本地单元测试 | 测试工程师 | 4h | JUnit 测试报告 + 数据库快照对比 |
| **Day 3-7** | 方案 B: 幂等守卫增强 + 并发锁 | 后端架构师 | 8h | 代码 PR + Code Review 记录 |
| **Week 2** | 方案 C: 生产环境数据清洗 | DBA + 财务 | 1 day | 备份文件 + 补偿执行日志 + 验证报告 |

---

## 五、风险与缓解措施

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| Flowable 建表失败导致应用启动崩溃 | 低 | 高 | 先在测试环境验证配置，再推送到生产 |
| 数据补偿错误导致二次数据污染 | 中 | 灾难 | 必须先 mysqldump 备份，且补偿后 24h 内密切监控 |
| 并发锁引入性能瓶颈 | 中 | 中 | 仅在高并发场景启用乐观锁，正常场景保持无锁 |
| 审批流程重新部署后历史流程实例丢失 | 高 | 中 | 仅对新提交的付款申请生效，历史 APPROVED 记录通过方案 C 补偿 |

---

## 六、验收标准

### 短期（方案 A 完成后）
✅ Flowable 表存在（act_re_procdef/act_ru_task/act_hi_procinst）  
✅ 4 笔 APPROVED 付款的对应合同 cumulative_paid ≠ NULL  
✅ 项目 total_expense 与 SUM(payment_apply.payment_amount) 一致  

### 中期（方案 B 完成后）
✅ 四类合同类型的集成测试全部通过  
✅ 并发重复回写场景被正确拦截（抛出 BusinessException）  
✅ Code Review 无重大缺陷  

### 长期（方案 C 完成后）
✅ 生产环境所有租户的 cumulative_paid/total_expense 数据一致  
✅ 财务月结报表准确无误  
✅ 供应商应付账款对账平衡  

---

**附录 A**: Flowable 官方文档 - database-schema-update 配置说明  
**附录 B**: 受阻台账登记确认（tasks.md 第 181 行）  
**附录 C**: SQL 探针原始数据（round1-complete-summary.md + round2-complete-summary.md）

---

**报告生成时间**: 2026-08-17  
**审计人员**: Qoder + Human Collaboration  
**下次迭代计划**: Day 1 执行完毕后召开评审会，确认是否进入 Day 2 验证阶段
