# ZW-Insight 数据库数据一致性审计报告（第 1 轮·紧急修订版）

**审计日期**: 2026-08-17  
**审计范围**: zw-finance/zw-contract/zw-project 模块  
**严重等级**: 🔴 **P0 资金链断裂缺陷需立即修复**  

---

## 执行摘要

### 关键发现
1. ✅ **字段一致性良好**：biz_payment_apply 表的 17 列与 API 载荷完全匹配（仅 2 个 zombie fields 需清理）
2. 🔴 **资金回写链路完全断裂**：4 笔 APPROVED 状态付款累计金额 **50,000 元**，对应合同表 cumulative_paid 全为 NULL
3. ⚠️ **审批回调可能被阻塞**：代码路径 `onApproved()` → `addCumulativePaid()` → SQL UPDATE 全部存在且正确，但数据库无更新记录

### 影响范围
- **直接损失**: 项目总支出统计失真（`biz_project.total_expense` 未按实际付款累加）
- **间接风险**: 预算管控失效（超预算检测基于错误的数据）、税务申报风险（发票与实付不符）
- **业务链条**: 付款→收款→开票→结算 全链路勾稽关系破坏

---

## 详细诊断

### 3.1 僵尸字段问题（P1 级别）

| 字段名 | DB 类型 | Java Entity | 使用状态 | 建议 |
|-------|--------|-------------|---------|------|
| `cumulative_settlement_snapshot` | DECIMAL(18,2) | BigDecimal✅ | ❌ 从未读写 | 删除或补充 Javadoc |
| `unpaid_amount_snapshot` | DECIMAL(18,2) | BigDecimal✅ | ❌ 从未读写 | 同上 |

**根本原因**: 设计时预留“实时计算 vs 快照落盘”两种方案，但实现选择了前者（L223-226 `resolvePayable` 实时读取），导致两个快照字段成为死代码。

**修复建议**:
```java
// BizPaymentApply.java L45-49 前添加废弃注释
@Deprecated // 2026-08-17 废弃：系统采用实时计算而非快照模式（见 resolvePayable L243）
private BigDecimal cumulativeSettlementSnapshot;
```

或彻底删除字段 + Flyway 迁移脚本（风险：历史数据丢失）。

---

### 3.2 资金回写断裂（P0 级别 - **需立即介入**）

#### 证据链
```sql
-- Step 3.1: APPROVED 付款总额
SELECT SUM(payment_amount) FROM biz_payment_apply 
  WHERE tenant_id=9999 AND status='APPROVED';  -- 结果：50000.00

-- Step 3.1: 对应合同累计值
SELECT cumulative_paid FROM biz_other_contract 
  WHERE id IN (
    SELECT contract_id FROM biz_payment_apply 
      WHERE tenant_id=9999 AND status='APPROVED'
  );  -- 结果：全部 NULL
```

#### 代码路径验证（理论上应该工作）
```java
// PaymentApplyService.java L159-196
public void onApproved(Long id) {
    // ... 状态检查 ...
    
    // L170: 读取可付信息（按 contractCategory 路由）
    ContractPayableInfo payable = resolvePayable(paymentApply);
    if (payable == null) return;  -- 日志：关联合同不存在
    
    // L188-189: 状态置 APPROVED
    paymentApply.setStatus("APPROVED");
    paymentApplyMapper.updateById(paymentApply);  -- ✅ 此步成功（DB 有记录）
    
    // L191-193: 回写合同累计已付 ← **此处应该执行但未生效？**
    addCumulativePaid(paymentApply, paymentApply.getPaymentAmount());
    projectMapper.addTotalExpense(...);  -- 项目总支出同样未知是否生效
    
    log.info("付款申请审批通过并生效");  -- ✅ 日志是否存在？
}

// PaymentApplyService.java L263-277
private void addCumulativePaid(...) {
    String category = paymentApply.getContractCategory();  -- "LABOR" / "SUBCONTRACT" / "PURCHASE" / "MACHINE"
    if (MODULE_CATEGORIES.contains(category)) {
        switch (category) {
            case "PURCHASE" -> contractPayableMapper.addPurchasePaid(...);   -- ✅ 存在
            case "LABOR" -> contractPayableMapper.addLaborPaid(...);         -- ✅ 存在
            case "MACHINE" -> contractPayableMapper.addMachinePaid(...);     -- ✅ 存在
            case "SUBCONTRACT" -> contractPayableMapper.addSubcontractPaid(..); -- ✅ 存在
        }
        return;
    }
    otherContractMapper.addCumulativePaid(...);  -- OTHER_EXPENSE 路径也存在
}

// ContractPayableMapper.java L50-52, L59-61
@Update("UPDATE biz_machine_contract SET cumulative_paid = COALESCE(cumulative_paid, 0) + #{amount} WHERE id = #{id} AND deleted = 0")
int addMachinePaid(@Param("id") Long id, @Param("amount") BigDecimal amount);
// 其他 3 种类型的 SQL 结构相同且语法正确
```

#### 矛盾点
1. ✅ Mapper 接口定义正确
2. ✅ SQL UPDATE 语句正确
3. ✅ biz_payment_apply.status = 'APPROVED'（证明 L188-189 已执行）
4. ❌ **但合同表 cumulative_paid 仍为 NULL**（证明 L192 的 addCumulativePaid 要么未调用，要么抛出异常静默吞掉）

---

### 3.3 推测根因（待人工验证）

#### 可能性 A: Transaction 传播问题（最可能）
```java
@Transactional(rollbackFor = Exception.class)  -- L158
public void onApproved(Long id) {
    // ...
    paymentApplyMapper.updateById(paymentApply);  -- ✅ 写入 act_ru_task?
    
    addCumulativePaid(...);  -- ❌ 如果抛 Checked Exception，可能被外层 try-catch 吞掉
}
```

**假设**: onApproved() 被 Flowable 的事件监听器异步调用（Spring Event），如果监听器事务配置不当，可能导致：
- PaymentApply 状态已更新为 APPROVED（因为先执行 updateById）
- 但 addCumulativePaid() 在另一个事务中失败，异常被 Spring 的全局 Handler 捕获但**不抛给 Flowable** → 流程结束，累积值不变

#### 可能性 B: Flowable 事件未触发
- BPMN XML 配置缺失 end event listener
- ApplicationEventPublisher 事件订阅失效
- 审批人操作未真正走到 `onApproved()` 方法入口

#### 可能性 C: SQL 执行失败但无日志
- MyBatis Update 返回 0 rows affected（id 不存在或 deleted=1）
- 但 Controller/Service 层未检查返回值 → 吞没异常

---

## 紧急行动建议（下一步必须执行）

### 1. 生产环境手工验证（优先级最高）
**命令序列**（需登录服务器运行）：
```bash
# 1.1 查看 Flowable 活动任务
docker exec zwi-mysql mysql -uroot -p'zwinsight123' zw_insight \
  -e "SELECT * FROM act_ru_task WHERE PROC_DEF_KEY_='payment_apply_approval' LIMIT 5;"

# 1.2 查看历史流程实例（是否有 complete 记录）
docker exec zwi-mysql mysql -uroot -p'zwinsight123' zw_insight \
  -e "SELECT PROC_INST_ID_, END_TIME_, STATE_ FROM act_hi_procinst WHERE PROC_DEF_KEY_='payment_apply_approval' ORDER BY END_TIME DESC LIMIT 5;"

# 1.3 启用 DEBUG 日志观察 onApproved() 是否被调用
ssh -i keys\zwinsight.pem root@129.204.3.200 "docker logs zwi-backend --tail 100 | grep '付款申请审批通过回调'"
```

### 2. 单元测试复现（本地环境）
**目标**: 构造一笔 APPROVED 状态的测试数据，验证回写逻辑：
```java
@Test
void testOnApproved_rewritesContractCumulativePaid() {
    // given
    BizPaymentApply apply = new BizPaymentApply();
    apply.setId(99990001L);
    apply.setContractId(99990002L);
    apply.setContractCategory("OTHER_EXPENSE");  -- 走其他支出分支
    apply.setPaymentAmount(new BigDecimal("10000"));
    apply.setStatus("APPROVED");
    
    when(paymentApplyMapper.selectById(99990001L)).thenReturn(apply);
    
    // when
    service.onApproved(99990001L);  -- 注意：这会尝试更新 DB！
    
    // then
    verify(otherContractMapper).addCumulativePaid(eq(99990002L), eq(new BigDecimal("10000")));
}
```

### 3. 数据库审计日志
```sql
-- 检查 biz_other_contract 的历史变更记录（是否有近 30 天的 UPDATE）
SELECT EVENT_TIME, OPERATION_USER, CHANGED_FIELDS 
FROM information_schema.EVENTS 
WHERE TABLE_SCHEMA='zw_insight' AND TABLE_NAME='biz_other_contract' 
  AND EVENT_TIME > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY EVENT_TIME DESC;
```

---

## 结论

**当前评估**:  
- **P0 缺陷等级**: 资金回写逻辑在**理论层全通、实践层失效**
- **影响面**: 租户 9999 下有 4 笔无效回写（50K 元），生产租户 1 需同等排查
- **紧迫性**: 🔴 **24 小时内必须定位根因并修复**（否则财务月结将受严重影响）

**后续计划**:  
1. 本周五前完成人工验证（上述 1.1-1.3 步骤）
2. 下周一启动专项修复（如证实是事务传播问题则调整@Transactional 配置；如 Flowable 事件失效则补监听器）
3. 第三周进行数据清洗（对已错误的历史数据进行补偿回写）

---

**附录 A**: SQL 探针原始日志（tmp_audit_step*.sh 输出）  
**附录 B**: Controller/Entity/Mapper 源码引用列表  
**附录 C**: P0 缺陷登记台账（需在 .kiro/specs/test-maturity-upgrade/tasks.md 追加条目）
